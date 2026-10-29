package com.keyloop.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.domain.Reason;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The booking flow on a real PostgreSQL. Each test builds the service with its own clock. */
@PostgresIntegrationTest
class BookAppointmentServiceTest {

  private static final Instant NOW = Instant.parse("2030-10-21T00:00:00Z");

  /** 09:00 London time (BST) on Tuesday 22 October 2030. */
  private static final Instant START = Instant.parse("2030-10-22T08:00:00Z");

  @Autowired private ReferenceDataPort referenceData;
  @Autowired private AvailabilityPort availability;
  @Autowired private AppointmentPort appointments;
  @Autowired private TransactionPort transactions;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private long dealershipId;
  private long serviceTypeId;
  private long bayId;
  private long vehicleId;
  private long otherVehicleId;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    bayId = fixtures.insertBay(dealershipId);
    long customerId = fixtures.insertCustomer();
    vehicleId = fixtures.insertVehicle(customerId, "AB12 CDE");
    otherVehicleId = fixtures.insertVehicle(customerId, "XY34 ZZZ");
  }

  @Test
  void storesConfirmedAppointmentWithEndFromServiceDuration() {
    long technicianId = skilledTechnician("Only");

    Appointment booked = service(NOW).book(command(START));

    assertThat(booked.id()).isNotNull();
    assertThat(booked)
        .isEqualTo(
            new Appointment(
                booked.id(),
                dealershipId,
                vehicleId,
                serviceTypeId,
                technicianId,
                bayId,
                START,
                Instant.parse("2030-10-22T09:30:00Z"),
                AppointmentStatus.CONFIRMED));
    assertThat(appointments.findById(booked.id())).contains(booked);
  }

  @Test
  void picksTechnicianWithFewestConfirmedAppointmentsThatDay() {
    long busier = skilledTechnician("Busier");
    long lessBusy = skilledTechnician("Less busy");
    bookOther(busier, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).technicianId()).isEqualTo(lessBusy);
  }

  @Test
  void breaksLoadTieByLowestTechnicianId() {
    long first = skilledTechnician("First");
    long second = skilledTechnician("Second");
    bookOther(first, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");
    bookOther(second, "2030-10-22T14:00:00Z", "2030-10-22T15:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).technicianId()).isEqualTo(first);
  }

  @Test
  void skipsBusyTechnicianEvenWhenLeastLoaded() {
    long busyNow = skilledTechnician("Busy now");
    long free = skilledTechnician("Free");
    bookOther(busyNow, "2030-10-22T08:30:00Z", "2030-10-22T09:00:00Z", "CONFIRMED");
    bookOther(free, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");
    bookOther(free, "2030-10-22T14:00:00Z", "2030-10-22T15:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).technicianId()).isEqualTo(free);
  }

  @Test
  void skipsTechnicianWithoutTheSkill() {
    fixtures.insertTechnician(dealershipId, "Unskilled");
    long skilled = skilledTechnician("Skilled");

    assertThat(service(NOW).book(command(START)).technicianId()).isEqualTo(skilled);
  }

  @Test
  void ignoresCancelledAppointmentsInDayLoad() {
    long first = skilledTechnician("First");
    long second = skilledTechnician("Second");
    bookOther(first, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CANCELLED");
    bookOther(first, "2030-10-22T14:00:00Z", "2030-10-22T15:00:00Z", "CANCELLED");
    bookOther(second, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).technicianId()).isEqualTo(first);
  }

  @Test
  void countsWholeLocalDayWhenClocksGoBack() {
    // 27 October 2030 in London lasts 25 hours: 2030-10-26T23:00Z to 2030-10-28T00:00Z.
    long first = skilledTechnician("First");
    long second = skilledTechnician("Second");
    bookOther(first, "2030-10-27T12:00:00Z", "2030-10-27T13:00:00Z", "CONFIRMED");
    bookOther(first, "2030-10-27T23:30:00Z", "2030-10-27T23:45:00Z", "CONFIRMED");
    bookOther(second, "2030-10-27T14:00:00Z", "2030-10-27T15:00:00Z", "CONFIRMED");

    Appointment booked = service(NOW).book(command(Instant.parse("2030-10-27T10:00:00Z")));

    assertThat(booked.technicianId()).isEqualTo(second);
  }

  @Test
  void doesNotCountNextDayWhenClocksGoForward() {
    // 31 March 2030 in London lasts 23 hours: 2030-03-31T00:00Z to 2030-03-31T23:00Z.
    long first = skilledTechnician("First");
    skilledTechnician("Second");
    bookOther(first, "2030-03-31T23:30:00Z", "2030-03-31T23:45:00Z", "CONFIRMED");

    Appointment booked =
        service(Instant.parse("2030-03-25T00:00:00Z"))
            .book(command(Instant.parse("2030-03-31T09:00:00Z")));

    assertThat(booked.technicianId()).isEqualTo(first);
  }

  @Test
  void picksLowestFreeBayId() {
    skilledTechnician("Technician");
    long otherTechnician = fixtures.insertTechnician(dealershipId, "Other");
    long secondBay = fixtures.insertBay(dealershipId);
    fixtures.insertBay(dealershipId);
    fixtures.insertAppointment(
        dealershipId,
        otherVehicleId,
        serviceTypeId,
        otherTechnician,
        bayId,
        START,
        Instant.parse("2030-10-22T09:00:00Z"),
        "CONFIRMED");

    assertThat(service(NOW).book(command(START)).bayId()).isEqualTo(secondBay);
  }

  @Test
  void readsNowFromTheClock() {
    skilledTechnician("Technician");

    assertThatThrownBy(() -> service(START).book(command(START)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.START_IN_PAST));
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void reportsFirstFailingRuleInTableOrder() {
    skilledTechnician("Technician");
    Instant pastAndAfterClosing = Instant.parse("2030-10-20T20:00:00Z");

    assertThatThrownBy(() -> service(NOW).book(command(pastAndAfterClosing)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.START_IN_PAST));
  }

  @Test
  void rejectsWithNoBayAndStoresNothingWhenTheOnlyBayIsTaken() {
    skilledTechnician("Technician");
    long otherTechnician = fixtures.insertTechnician(dealershipId, "Other");
    fixtures.insertAppointment(
        dealershipId,
        otherVehicleId,
        serviceTypeId,
        otherTechnician,
        bayId,
        START,
        Instant.parse("2030-10-22T09:00:00Z"),
        "CONFIRMED");

    assertThatThrownBy(() -> service(NOW).book(command(START)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NO_BAY));
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void rejectsWithVehicleBusyAndStoresNothing() {
    long technicianId = skilledTechnician("Technician");
    long secondBay = fixtures.insertBay(dealershipId);
    fixtures.insertAppointment(
        dealershipId,
        vehicleId,
        serviceTypeId,
        technicianId,
        secondBay,
        Instant.parse("2030-10-22T07:00:00Z"),
        Instant.parse("2030-10-22T08:30:00Z"),
        "CONFIRMED");

    assertThatThrownBy(() -> service(NOW).book(command(START)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.VEHICLE_BUSY));
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void rejectsUnknownDealershipWithItsId() {
    var command = new BookAppointmentCommand(999_999, vehicleId, serviceTypeId, START);

    assertThatThrownBy(() -> service(NOW).book(command))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> {
              assertThat(e.reason()).isEqualTo(Reason.DEALERSHIP_NOT_FOUND);
              assertThat(e.id()).hasValue(999_999);
            });
  }

  private BookAppointmentService service(Instant now) {
    return new BookAppointmentService(
        referenceData, availability, appointments, transactions, Clock.fixed(now, ZoneOffset.UTC));
  }

  private BookAppointmentCommand command(Instant start) {
    return new BookAppointmentCommand(dealershipId, vehicleId, serviceTypeId, start);
  }

  private long skilledTechnician(String name) {
    long technicianId = fixtures.insertTechnician(dealershipId, name);
    fixtures.insertSkill(technicianId, serviceTypeId);
    return technicianId;
  }

  /**
   * Books another vehicle with this technician, on a bay of its own. The booking under test then
   * still gets the first bay.
   */
  private void bookOther(long technicianId, String start, String end, String status) {
    long ownBay = fixtures.insertBay(dealershipId);
    fixtures.insertAppointment(
        dealershipId,
        otherVehicleId,
        serviceTypeId,
        technicianId,
        ownBay,
        Instant.parse(start),
        Instant.parse(end),
        status);
  }
}
