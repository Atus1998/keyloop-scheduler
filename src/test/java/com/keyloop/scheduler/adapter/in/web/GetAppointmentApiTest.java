package com.keyloop.scheduler.adapter.in.web;

import static com.keyloop.scheduler.domain.AppointmentStatus.CANCELLED;
import static com.keyloop.scheduler.domain.AppointmentStatus.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** GET /appointments/{id} through the whole stack, on a real PostgreSQL. */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class GetAppointmentApiTest {

  private static final Instant START = Instant.parse("2026-11-03T02:00:00Z");
  private static final Instant END = Instant.parse("2026-11-03T03:30:00Z");

  @Autowired private MockMvcTester mvc;
  @Autowired private AppointmentPort appointments;
  @Autowired private JdbcTemplate jdbc;

  private long dealershipId;
  private long serviceTypeId;
  private long technicianId;
  private long bayId;
  private long vehicleId;

  @BeforeEach
  void setUp() {
    var fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership();
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    technicianId = fixtures.insertTechnician(dealershipId, "Technician");
    bayId = fixtures.insertBay(dealershipId);
    vehicleId = fixtures.insertVehicle(fixtures.insertCustomer(), "51A-111.11");
  }

  @Test
  void returnsConfirmedAppointmentWithNineFieldsInUtc() {
    Appointment stored = store(CONFIRMED);

    assertThat(mvc.get().uri("/appointments/{id}", stored.id()))
        .hasStatusOk()
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": %d,
              "status": "CONFIRMED",
              "dealershipId": %d,
              "vehicleId": %d,
              "serviceTypeId": %d,
              "technicianId": %d,
              "bayId": %d,
              "startTime": "2026-11-03T02:00:00Z",
              "endTime": "2026-11-03T03:30:00Z"
            }
            """
                .formatted(
                    stored.id(), dealershipId, vehicleId, serviceTypeId, technicianId, bayId));
  }

  @Test
  void returnsCancelledAppointmentWithCancelledStatus() {
    Appointment stored = store(CANCELLED);

    assertThat(mvc.get().uri("/appointments/{id}", stored.id()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("CANCELLED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "9223372036854775807"})
  void returns404WithTopLevelReasonWhenAppointmentDoesNotExist(String id) {
    var result = mvc.get().uri("/appointments/{id}", id);

    assertThat(result)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("APPOINTMENT_NOT_FOUND");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("Appointment " + id + " was not found.");
    assertThat(result).bodyJson().doesNotHavePath("$.properties");
  }

  @Test
  void returns400WithTopLevelReasonForNonNumericId() {
    var result = mvc.get().uri("/appointments/abc");

    assertThat(result)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("INVALID_REQUEST");
    assertThat(result).bodyJson().doesNotHavePath("$.properties");
  }

  @Test
  void treatsLeadingZerosAsSameId() {
    Appointment stored = store(CONFIRMED);

    assertThat(mvc.get().uri("/appointments/00" + stored.id()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo(stored.id().intValue());
  }

  private Appointment store(AppointmentStatus status) {
    return appointments.insert(
        new Appointment(
            null, dealershipId, vehicleId, serviceTypeId, technicianId, bayId, START, END, status));
  }
}
