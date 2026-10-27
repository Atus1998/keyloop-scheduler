package com.keyloop.scheduler.adapter.out.persistence;

import static com.keyloop.scheduler.domain.AppointmentStatus.CANCELLED;
import static com.keyloop.scheduler.domain.AppointmentStatus.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.service.ResourceConflictException;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class AppointmentAdapterTest {

  private static final Instant AT_10_00 = Instant.parse("2026-11-03T10:00:00Z");
  private static final Instant AT_10_30 = Instant.parse("2026-11-03T10:30:00Z");
  private static final Instant AT_11_00 = Instant.parse("2026-11-03T11:00:00Z");
  private static final Instant AT_11_30 = Instant.parse("2026-11-03T11:30:00Z");
  private static final Instant AT_12_00 = Instant.parse("2026-11-03T12:00:00Z");

  @Autowired private AppointmentPort appointments;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private long dealershipId;
  private long serviceTypeId;
  private long technician1;
  private long technician2;
  private long bay1;
  private long bay2;
  private long vehicle1;
  private long vehicle2;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership();
    serviceTypeId = fixtures.insertServiceType("Oil change", 60);
    technician1 = fixtures.insertTechnician(dealershipId, "Technician 1");
    technician2 = fixtures.insertTechnician(dealershipId, "Technician 2");
    bay1 = fixtures.insertBay(dealershipId);
    bay2 = fixtures.insertBay(dealershipId);
    long customerId = fixtures.insertCustomer();
    vehicle1 = fixtures.insertVehicle(customerId, "51A-111.11");
    vehicle2 = fixtures.insertVehicle(customerId, "51A-222.22");
  }

  @Test
  void returnsStoredAppointmentWithId() {
    Appointment toStore = appointment(technician1, bay1, vehicle1, AT_10_00, AT_11_00, CONFIRMED);

    Appointment stored = appointments.insert(toStore);

    assertThat(stored.id()).isNotNull();
    assertThat(stored).isEqualTo(toStore.withId(stored.id()));
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void rejectsOverlappingConfirmedForSameTechnician() {
    appointments.insert(appointment(technician1, bay1, vehicle1, AT_10_00, AT_11_00, CONFIRMED));

    assertExclusion(
        appointment(technician1, bay2, vehicle2, AT_10_30, AT_11_30, CONFIRMED),
        "appointment_technician_no_overlap");
  }

  @Test
  void rejectsOverlappingConfirmedForSameBay() {
    appointments.insert(appointment(technician1, bay1, vehicle1, AT_10_00, AT_11_00, CONFIRMED));

    assertExclusion(
        appointment(technician2, bay1, vehicle2, AT_10_30, AT_11_30, CONFIRMED),
        "appointment_bay_no_overlap");
  }

  @Test
  void rejectsOverlappingConfirmedForSameVehicle() {
    appointments.insert(appointment(technician1, bay1, vehicle1, AT_10_00, AT_11_00, CONFIRMED));

    assertExclusion(
        appointment(technician2, bay2, vehicle1, AT_10_30, AT_11_30, CONFIRMED),
        "appointment_vehicle_no_overlap");
  }

  @Test
  void insertsBackToBackAppointmentsForSameResources() {
    appointments.insert(appointment(technician1, bay1, vehicle1, AT_10_00, AT_11_00, CONFIRMED));
    appointments.insert(appointment(technician1, bay1, vehicle1, AT_11_00, AT_12_00, CONFIRMED));

    assertThat(fixtures.countAppointments()).isEqualTo(2);
  }

  @Test
  void insertsOverlapWhenExistingIsCancelled() {
    appointments.insert(appointment(technician1, bay1, vehicle1, AT_10_00, AT_11_00, CANCELLED));
    appointments.insert(appointment(technician1, bay1, vehicle1, AT_10_30, AT_11_30, CONFIRMED));

    assertThat(fixtures.countAppointments()).isEqualTo(2);
  }

  @Test
  void rejectsEndEqualToStart() {
    assertTimeCheckFails(appointment(technician1, bay1, vehicle1, AT_10_00, AT_10_00, CONFIRMED));
  }

  @Test
  void rejectsEndBeforeStart() {
    assertTimeCheckFails(appointment(technician1, bay1, vehicle1, AT_11_00, AT_10_00, CONFIRMED));
  }

  @Test
  void rejectsAppointmentThatAlreadyHasId() {
    Appointment withId =
        appointment(technician1, bay1, vehicle1, AT_10_00, AT_11_00, CONFIRMED).withId(42);

    assertThatThrownBy(() -> appointments.insert(withId))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void storesInstantsAsUtcRegardlessOfJvmZone() {
    Appointment stored =
        appointments.insert(
            appointment(
                technician1,
                bay1,
                vehicle1,
                Instant.parse("2026-11-03T02:00:00Z"),
                Instant.parse("2026-11-03T03:30:00Z"),
                CONFIRMED));

    String startUtc =
        jdbc.queryForObject(
            "SELECT to_char(start_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI')"
                + " FROM appointment WHERE id = ?",
            String.class,
            stored.id());
    assertThat(startUtc).isEqualTo("2026-11-03 02:00");
  }

  @Test
  void returnsTheInstantsThatWereStored() {
    Appointment stored =
        appointments.insert(
            appointment(
                technician1,
                bay1,
                vehicle1,
                Instant.parse("2026-11-03T10:00:00.123456789Z"),
                Instant.parse("2026-11-03T11:00:00.123456789Z"),
                CONFIRMED));

    OffsetDateTime startInDb =
        jdbc.queryForObject(
            "SELECT start_at FROM appointment WHERE id = ?", OffsetDateTime.class, stored.id());
    OffsetDateTime endInDb =
        jdbc.queryForObject(
            "SELECT end_at FROM appointment WHERE id = ?", OffsetDateTime.class, stored.id());
    assertThat(stored.startAt()).isEqualTo(startInDb.toInstant());
    assertThat(stored.endAt()).isEqualTo(endInDb.toInstant());
  }

  private void assertExclusion(Appointment loser, String constraint) {
    assertThatThrownBy(() -> appointments.insert(loser))
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            e -> assertThat(e.kind()).isEqualTo(ResourceConflictException.Kind.EXCLUSION))
        .hasStackTraceContaining(constraint);
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  private void assertTimeCheckFails(Appointment invalid) {
    Throwable thrown = catchThrowable(() -> appointments.insert(invalid));

    assertThat(thrown).isNotNull().isNotInstanceOf(ResourceConflictException.class);
    assertThat(SqlStates.of(thrown)).contains("23514");
    assertThat(thrown).hasStackTraceContaining("appointment_time_valid");
  }

  private Appointment appointment(
      long technicianId,
      long bayId,
      long vehicleId,
      Instant start,
      Instant end,
      AppointmentStatus status) {
    return new Appointment(
        null, dealershipId, vehicleId, serviceTypeId, technicianId, bayId, start, end, status);
  }
}
