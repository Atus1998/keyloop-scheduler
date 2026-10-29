package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * POST /appointments through the whole stack, on a real PostgreSQL. The clock is fixed at
 * 2030-10-21T00:00:00Z ({@code TestClockConfiguration}). The dealership is in London, open 09:00 to
 * 18:00; 22 October 2030 is in BST (UTC+1).
 */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class BookAppointmentApiTest {

  /** 09:00 in London, sent with a Vietnam offset. */
  private static final String START = "2030-10-22T15:00:00+07:00";

  private static final long UNKNOWN_ID = 999_999;

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private long dealershipId;
  private long serviceTypeId;
  private long technicianId;
  private long bayId;
  private long vehicleId;
  private long otherVehicleId;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    technicianId = fixtures.insertTechnician(dealershipId, "Technician");
    fixtures.insertSkill(technicianId, serviceTypeId);
    bayId = fixtures.insertBay(dealershipId);
    long customerId = fixtures.insertCustomer();
    vehicleId = fixtures.insertVehicle(customerId, "AB12 CDE");
    otherVehicleId = fixtures.insertVehicle(customerId, "XY34 ZZZ");
  }

  @Test
  void returns201WithLocationAndUtcBodyWhenEverythingIsFree() {
    MvcTestResult result = post(request(START));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    long id = storedId();
    assertThat(result).containsHeader("Location");
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/appointments/" + id);
    assertThat(result)
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
              "startTime": "2030-10-22T08:00:00Z",
              "endTime": "2030-10-22T09:30:00Z"
            }
            """
                .formatted(id, dealershipId, vehicleId, serviceTypeId, technicianId, bayId));
  }

  @Test
  void getReturnsSameBodyAsPost() throws Exception {
    MvcTestResult created = post(request(START));
    assertThat(created).hasStatus(HttpStatus.CREATED);
    String createdBody = created.getResponse().getContentAsString();

    assertThat(mvc.get().uri(created.getResponse().getHeader("Location")))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(createdBody);
  }

  @Test
  void ignoresUnknownJsonFields() {
    String body =
        """
        {"dealershipId": %d, "vehicleId": %d, "serviceTypeId": %d,
         "startTime": "%s", "comment": "please be quick", "technicianId": 42}
        """
            .formatted(dealershipId, vehicleId, serviceTypeId, START);

    assertThat(post(body)).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void acceptsStartOnDay90OfDealershipCalendar() {
    assertThat(post(request("2031-01-19T10:00:00Z"))).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void returns400ForMalformedJsonAndStoresNothing() {
    assertRejected(post("{\"dealershipId\": 1,"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void returns400ForMissingFieldAndStoresNothing() {
    String body =
        """
        {"dealershipId": %d, "vehicleId": %d, "startTime": "%s"}
        """
            .formatted(dealershipId, vehicleId, START);

    assertRejected(post(body), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void returns404ForUnknownDealership() {
    MvcTestResult result = post(request(UNKNOWN_ID, vehicleId, serviceTypeId, START));

    assertRejected(result, HttpStatus.NOT_FOUND, "DEALERSHIP_NOT_FOUND");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("Dealership " + UNKNOWN_ID + " was not found.");
  }

  @Test
  void returns404ForUnknownVehicle() {
    MvcTestResult result = post(request(dealershipId, UNKNOWN_ID, serviceTypeId, START));

    assertRejected(result, HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("Vehicle " + UNKNOWN_ID + " was not found.");
  }

  @Test
  void returns404ForUnknownServiceType() {
    MvcTestResult result = post(request(dealershipId, vehicleId, UNKNOWN_ID, START));

    assertRejected(result, HttpStatus.NOT_FOUND, "SERVICE_TYPE_NOT_FOUND");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("Service type " + UNKNOWN_ID + " was not found.");
  }

  @Test
  void reportsUnknownDealershipFirstWhenAllIdsAreUnknown() {
    assertRejected(
        post(request(UNKNOWN_ID, UNKNOWN_ID, UNKNOWN_ID, START)),
        HttpStatus.NOT_FOUND,
        "DEALERSHIP_NOT_FOUND");
  }

  @Test
  void reportsUnknownVehicleBeforeUnknownServiceType() {
    assertRejected(
        post(request(dealershipId, UNKNOWN_ID, UNKNOWN_ID, START)),
        HttpStatus.NOT_FOUND,
        "VEHICLE_NOT_FOUND");
  }

  @Test
  void returns422WhenStartHasSeconds() {
    assertUnprocessable("2030-10-22T09:00:30+01:00", "START_NOT_WHOLE_MINUTE");
  }

  @Test
  void returns422WhenStartIsNowOnTheApplicationClock() {
    assertUnprocessable("2030-10-21T00:00:00Z", "START_IN_PAST");
  }

  @Test
  void returns422WhenStartIsOnDay91OfDealershipCalendar() {
    assertUnprocessable("2031-01-20T10:00:00Z", "START_TOO_FAR_AHEAD");
  }

  @Test
  void returns422WhenServiceEndsAfterClosing() {
    assertUnprocessable("2030-10-22T17:00:00+01:00", "OUTSIDE_OPENING_HOURS");
  }

  @Test
  void returns422WhenNoTechnicianAtTheDealershipHasTheSkill() {
    long tyres = fixtures.insertServiceType("Tyres", 60);
    long elsewhere = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    fixtures.insertSkill(fixtures.insertTechnician(elsewhere, "Elsewhere"), tyres);

    assertRejected(
        post(request(dealershipId, vehicleId, tyres, START)),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "SERVICE_NOT_OFFERED");
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void returns409WhenVehicleIsBookedAtAnotherDealership() {
    long elsewhere = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    long elsewhereTechnician = fixtures.insertTechnician(elsewhere, "Elsewhere");
    fixtures.insertAppointment(
        elsewhere,
        vehicleId,
        serviceTypeId,
        elsewhereTechnician,
        fixtures.insertBay(elsewhere),
        at("2030-10-22T09:00:00Z"),
        at("2030-10-22T10:00:00Z"),
        "CONFIRMED");

    assertConflict("VEHICLE_BUSY", 1);
  }

  @Test
  void reportsVehicleBusyBeforeBusy() {
    book(vehicleId, technicianId, bayId, "CONFIRMED");

    assertConflict("VEHICLE_BUSY", 1);
  }

  @Test
  void returns409NoTechnicianWhenOnlyTheTechnicianIsTaken() {
    long secondBay = fixtures.insertBay(dealershipId);
    fixtures.insertAppointment(
        dealershipId,
        otherVehicleId,
        serviceTypeId,
        technicianId,
        secondBay,
        at("2030-10-22T09:00:00Z"),
        at("2030-10-22T10:00:00Z"),
        "CONFIRMED");

    assertConflict("NO_TECHNICIAN", 1);
  }

  @Test
  void returns409NoBayWhenOnlyTheBayIsTaken() {
    long otherTechnician = fixtures.insertTechnician(dealershipId, "Other");
    book(otherVehicleId, otherTechnician, bayId, "CONFIRMED");

    assertConflict("NO_BAY", 1);
  }

  @Test
  void returns409BusyWhenTechnicianAndBayAreTaken() {
    book(otherVehicleId, technicianId, bayId, "CONFIRMED");

    assertConflict("BUSY", 1);
  }

  @Test
  void acceptsBookingRightAfterAnotherOne() {
    fixtures.insertAppointment(
        dealershipId,
        vehicleId,
        serviceTypeId,
        technicianId,
        bayId,
        at("2030-10-22T06:30:00Z"),
        at("2030-10-22T08:00:00Z"),
        "CONFIRMED");

    assertThat(post(request(START))).hasStatus(HttpStatus.CREATED);
    assertThat(fixtures.countAppointments()).isEqualTo(2);
  }

  @Test
  void ignoresCancelledOverlap() {
    book(vehicleId, technicianId, bayId, "CANCELLED");

    assertThat(post(request(START))).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void returns200WithSameIdAndBodyAndNoLocationOnSequentialRetry() throws Exception {
    MvcTestResult first = post(request(START));
    assertThat(first).hasStatus(HttpStatus.CREATED);
    String firstBody = first.getResponse().getContentAsString();

    MvcTestResult second = post(request(START));

    assertThat(second)
        .hasStatus(HttpStatus.OK)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .doesNotContainHeader("Location");
    assertThat(second).bodyJson().isStrictlyEqualTo(firstBody);
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void matchesRetrySentWithAnotherOffsetForTheSameInstant() {
    MvcTestResult first = post(request(START));
    assertThat(first).hasStatus(HttpStatus.CREATED);
    long id = storedId();

    MvcTestResult second = post(request("2030-10-22T08:00:00Z"));

    assertThat(second).hasStatus(HttpStatus.OK);
    assertThat(second).bodyJson().extractingPath("$.id").isEqualTo((int) id);
  }

  @Test
  void returns404ForUnknownServiceTypeEvenWhenVehicleIsBookedAtThatStart() {
    assertThat(post(request(START))).hasStatus(HttpStatus.CREATED);

    assertRejected(
        post(request(dealershipId, vehicleId, UNKNOWN_ID, START)),
        HttpStatus.NOT_FOUND,
        "SERVICE_TYPE_NOT_FOUND");
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void returns404ForUnknownDealershipEvenWhenVehicleIsBookedAtThatStart() {
    assertThat(post(request(START))).hasStatus(HttpStatus.CREATED);

    assertRejected(
        post(request(UNKNOWN_ID, vehicleId, serviceTypeId, START)),
        HttpStatus.NOT_FOUND,
        "DEALERSHIP_NOT_FOUND");
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void returns201WhenMatchingAppointmentIsCancelled() {
    fixtures.insertAppointment(
        dealershipId,
        vehicleId,
        serviceTypeId,
        technicianId,
        bayId,
        at("2030-10-22T08:00:00Z"),
        at("2030-10-22T09:30:00Z"),
        "CANCELLED");

    assertThat(post(request(START))).hasStatus(HttpStatus.CREATED);
    assertThat(fixtures.countAppointments()).isEqualTo(2);
  }

  @Test
  void returns409VehicleBusyWhenSameStartButOtherServiceType() {
    long tyres = fixtures.insertServiceType("Tyres", 60);
    fixtures.insertSkill(technicianId, tyres);
    fixtures.insertBay(dealershipId);
    assertThat(post(request(START))).hasStatus(HttpStatus.CREATED);

    assertRejected(
        post(request(dealershipId, vehicleId, tyres, START)), HttpStatus.CONFLICT, "VEHICLE_BUSY");
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  private void assertUnprocessable(String startTime, String reason) {
    assertRejected(post(request(startTime)), HttpStatus.UNPROCESSABLE_CONTENT, reason);
    assertThat(fixtures.countAppointments()).isZero();
  }

  private void assertConflict(String reason, int appointmentsBefore) {
    assertRejected(post(request(START)), HttpStatus.CONFLICT, reason);
    assertThat(fixtures.countAppointments()).isEqualTo(appointmentsBefore);
  }

  private static void assertRejected(MvcTestResult result, HttpStatus status, String reason) {
    assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo(reason);
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
  }

  /** An overlapping appointment, 09:00 to 10:00 UTC on the booking day. */
  private void book(long vehicle, long technician, long bay, String status) {
    fixtures.insertAppointment(
        dealershipId,
        vehicle,
        serviceTypeId,
        technician,
        bay,
        at("2030-10-22T09:00:00Z"),
        at("2030-10-22T10:00:00Z"),
        status);
  }

  private MvcTestResult post(String body) {
    return mvc.post()
        .uri("/appointments")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private String request(String startTime) {
    return request(dealershipId, vehicleId, serviceTypeId, startTime);
  }

  private static String request(long dealership, long vehicle, long serviceType, String startTime) {
    return """
        {"dealershipId": %d, "vehicleId": %d, "serviceTypeId": %d, "startTime": "%s"}
        """
        .formatted(dealership, vehicle, serviceType, startTime);
  }

  private long storedId() {
    return jdbc.queryForObject("SELECT id FROM appointment", Long.class);
  }

  private static Instant at(String instant) {
    return Instant.parse(instant);
  }
}
