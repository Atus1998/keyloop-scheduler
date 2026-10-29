package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.domain.DealershipDay;
import com.keyloop.scheduler.domain.TechnicianCandidate;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class AvailabilityAdapterTest {

  /** 09:00 to 10:30 in London (BST). */
  private static final Instant START = Instant.parse("2030-10-22T08:00:00Z");

  private static final Instant END = Instant.parse("2030-10-22T09:30:00Z");

  /** 22 October 2030 in London. */
  private static final DealershipDay DAY =
      new DealershipDay(
          Instant.parse("2030-10-21T23:00:00Z"), Instant.parse("2030-10-22T23:00:00Z"));

  @Autowired private AvailabilityPort availability;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private long dealershipId;
  private long otherDealershipId;
  private long serviceTypeId;
  private long firstTechnicianId;
  private long secondTechnicianId;
  private long unskilledTechnicianId;
  private long otherDealershipTechnicianId;
  private long firstBayId;
  private long secondBayId;
  private long otherDealershipBayId;
  private long vehicleId;
  private long otherVehicleId;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    otherDealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    firstTechnicianId = skilledTechnician(dealershipId, "First");
    secondTechnicianId = skilledTechnician(dealershipId, "Second");
    unskilledTechnicianId = fixtures.insertTechnician(dealershipId, "Unskilled");
    otherDealershipTechnicianId = skilledTechnician(otherDealershipId, "Elsewhere");
    firstBayId = fixtures.insertBay(dealershipId);
    secondBayId = fixtures.insertBay(dealershipId);
    otherDealershipBayId = fixtures.insertBay(otherDealershipId);
    long customerId = fixtures.insertCustomer();
    vehicleId = fixtures.insertVehicle(customerId, "AB12 CDE");
    otherVehicleId = fixtures.insertVehicle(customerId, "XY34 ZZZ");
  }

  @Test
  void returnsQualifiedTechniciansOfTheDealershipOrderedById() {
    assertThat(freeTechnicians())
        .containsExactly(
            new TechnicianCandidate(firstTechnicianId, 0),
            new TechnicianCandidate(secondTechnicianId, 0));
  }

  @Test
  void excludesTechnicianWithOverlappingConfirmedAppointment() {
    book(firstTechnicianId, firstBayId, "2030-10-22T09:00:00Z", "2030-10-22T10:00:00Z");

    assertThat(freeTechnicians()).containsExactly(new TechnicianCandidate(secondTechnicianId, 0));
  }

  /** Every way a row can overlap {@code [08:00, 09:30)}. */
  @ParameterizedTest
  @CsvSource({
    "2030-10-22T07:30:00Z, 2030-10-22T08:30:00Z",
    "2030-10-22T07:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:30:00Z, 2030-10-22T09:00:00Z",
    "2030-10-22T09:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:00:00Z, 2030-10-22T09:30:00Z"
  })
  void excludesTechnicianForEveryOverlapShape(Instant start, Instant end) {
    book(firstTechnicianId, firstBayId, start, end, "CONFIRMED");

    assertThat(freeTechnicians()).containsExactly(new TechnicianCandidate(secondTechnicianId, 0));
  }

  @ParameterizedTest
  @CsvSource({
    "2030-10-22T07:30:00Z, 2030-10-22T08:30:00Z",
    "2030-10-22T07:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:30:00Z, 2030-10-22T09:00:00Z",
    "2030-10-22T09:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:00:00Z, 2030-10-22T09:30:00Z"
  })
  void excludesBayForEveryOverlapShape(Instant start, Instant end) {
    book(unskilledTechnicianId, firstBayId, start, end, "CONFIRMED");

    assertThat(availability.findFreeBayIds(dealershipId, START, END)).containsExactly(secondBayId);
  }

  @ParameterizedTest
  @CsvSource({
    "2030-10-22T07:30:00Z, 2030-10-22T08:30:00Z",
    "2030-10-22T07:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:30:00Z, 2030-10-22T09:00:00Z",
    "2030-10-22T09:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:00:00Z, 2030-10-22T09:30:00Z"
  })
  void vehicleIsBusyForEveryOverlapShape(Instant start, Instant end) {
    bookVehicle(start, end, "CONFIRMED");

    assertThat(availability.isVehicleBusy(vehicleId, START, END)).isTrue();
  }

  @Test
  void keepsTechnicianWithBackToBackAppointments() {
    book(firstTechnicianId, firstBayId, "2030-10-22T06:30:00Z", "2030-10-22T08:00:00Z");
    book(firstTechnicianId, firstBayId, "2030-10-22T09:30:00Z", "2030-10-22T10:00:00Z");

    assertThat(freeTechnicians())
        .containsExactly(
            new TechnicianCandidate(firstTechnicianId, 2),
            new TechnicianCandidate(secondTechnicianId, 0));
  }

  @Test
  void ignoresCancelledOverlapForTechnicians() {
    book(firstTechnicianId, firstBayId, START, END, "CANCELLED");

    assertThat(freeTechnicians())
        .containsExactly(
            new TechnicianCandidate(firstTechnicianId, 0),
            new TechnicianCandidate(secondTechnicianId, 0));
  }

  @Test
  void countsConfirmedAppointmentsThatStartWithinTheDay() {
    book(firstTechnicianId, firstBayId, DAY.start(), DAY.start().plusSeconds(1800), "CONFIRMED");
    book(firstTechnicianId, firstBayId, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z");
    book(
        firstTechnicianId,
        firstBayId,
        Instant.parse("2030-10-22T14:00:00Z"),
        Instant.parse("2030-10-22T15:00:00Z"),
        "CANCELLED");
    book(firstTechnicianId, firstBayId, DAY.end(), DAY.end().plusSeconds(1800), "CONFIRMED");

    assertThat(freeTechnicians())
        .containsExactly(
            new TechnicianCandidate(firstTechnicianId, 2),
            new TechnicianCandidate(secondTechnicianId, 0));
  }

  @Test
  void doesNotCountAppointmentThatStartsJustBeforeTheDay() {
    book(
        firstTechnicianId,
        firstBayId,
        DAY.start().minusSeconds(1800),
        DAY.start().plusSeconds(1800),
        "CONFIRMED");

    assertThat(freeTechnicians()).first().isEqualTo(new TechnicianCandidate(firstTechnicianId, 0));
  }

  @Test
  void returnsFreeBaysOfTheDealershipOrderedById() {
    assertThat(availability.findFreeBayIds(dealershipId, START, END))
        .containsExactly(firstBayId, secondBayId);
  }

  @Test
  void excludesBayWithOverlappingConfirmedAppointment() {
    book(unskilledTechnicianId, firstBayId, "2030-10-22T09:00:00Z", "2030-10-22T10:00:00Z");

    assertThat(availability.findFreeBayIds(dealershipId, START, END)).containsExactly(secondBayId);
  }

  @Test
  void keepsBayWithBackToBackAppointmentsAndCancelledOverlap() {
    book(unskilledTechnicianId, firstBayId, "2030-10-22T06:30:00Z", "2030-10-22T08:00:00Z");
    book(unskilledTechnicianId, firstBayId, "2030-10-22T09:30:00Z", "2030-10-22T10:00:00Z");
    book(unskilledTechnicianId, secondBayId, START, END, "CANCELLED");

    assertThat(availability.findFreeBayIds(dealershipId, START, END))
        .containsExactly(firstBayId, secondBayId);
  }

  @Test
  void vehicleIsBusyWithOverlappingAppointmentAtAnotherDealership() {
    fixtures.insertAppointment(
        otherDealershipId,
        vehicleId,
        serviceTypeId,
        otherDealershipTechnicianId,
        otherDealershipBayId,
        Instant.parse("2030-10-22T09:00:00Z"),
        Instant.parse("2030-10-22T10:00:00Z"),
        "CONFIRMED");

    assertThat(availability.isVehicleBusy(vehicleId, START, END)).isTrue();
    assertThat(availability.isVehicleBusy(otherVehicleId, START, END)).isFalse();
  }

  @Test
  void vehicleIsFreeWithBackToBackOrCancelledAppointments() {
    bookVehicle(Instant.parse("2030-10-22T06:30:00Z"), START, "CONFIRMED");
    bookVehicle(END, Instant.parse("2030-10-22T10:00:00Z"), "CONFIRMED");
    bookVehicle(START, END, "CANCELLED");

    assertThat(availability.isVehicleBusy(vehicleId, START, END)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "2030-10-22T07:30:00Z, 2030-10-22T08:30:00Z",
    "2030-10-22T07:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:30:00Z, 2030-10-22T09:00:00Z",
    "2030-10-22T09:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:00:00Z, 2030-10-22T09:30:00Z"
  })
  void technicianIsBusyForEveryOverlapShape(Instant start, Instant end) {
    book(firstTechnicianId, firstBayId, start, end, "CONFIRMED");

    assertThat(availability.isTechnicianBusy(firstTechnicianId, START, END)).isTrue();
  }

  @Test
  void technicianIsFreeWithBackToBackCancelledOrOtherTechniciansAppointments() {
    book(firstTechnicianId, firstBayId, "2030-10-22T06:30:00Z", "2030-10-22T08:00:00Z");
    book(firstTechnicianId, firstBayId, "2030-10-22T09:30:00Z", "2030-10-22T10:00:00Z");
    book(firstTechnicianId, secondBayId, START, END, "CANCELLED");
    book(secondTechnicianId, secondBayId, START, END, "CONFIRMED");

    assertThat(availability.isTechnicianBusy(firstTechnicianId, START, END)).isFalse();
    assertThat(availability.isTechnicianBusy(secondTechnicianId, START, END)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "2030-10-22T07:30:00Z, 2030-10-22T08:30:00Z",
    "2030-10-22T07:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:30:00Z, 2030-10-22T09:00:00Z",
    "2030-10-22T09:00:00Z, 2030-10-22T10:00:00Z",
    "2030-10-22T08:00:00Z, 2030-10-22T09:30:00Z"
  })
  void bayIsBusyForEveryOverlapShape(Instant start, Instant end) {
    book(unskilledTechnicianId, firstBayId, start, end, "CONFIRMED");

    assertThat(availability.isBayBusy(firstBayId, START, END)).isTrue();
  }

  @Test
  void bayIsFreeWithBackToBackCancelledOrOtherBaysAppointments() {
    book(unskilledTechnicianId, firstBayId, "2030-10-22T06:30:00Z", "2030-10-22T08:00:00Z");
    book(unskilledTechnicianId, firstBayId, "2030-10-22T09:30:00Z", "2030-10-22T10:00:00Z");
    book(firstTechnicianId, firstBayId, START, END, "CANCELLED");
    book(secondTechnicianId, secondBayId, START, END, "CONFIRMED");

    assertThat(availability.isBayBusy(firstBayId, START, END)).isFalse();
    assertThat(availability.isBayBusy(secondBayId, START, END)).isTrue();
  }

  private List<TechnicianCandidate> freeTechnicians() {
    return availability.findFreeQualifiedTechnicians(dealershipId, serviceTypeId, START, END, DAY);
  }

  private long skilledTechnician(long dealership, String name) {
    long technicianId = fixtures.insertTechnician(dealership, name);
    fixtures.insertSkill(technicianId, serviceTypeId);
    return technicianId;
  }

  private void book(long technicianId, long bayId, String start, String end) {
    book(technicianId, bayId, Instant.parse(start), Instant.parse(end), "CONFIRMED");
  }

  /** Another vehicle, so only the technician and the bay are taken. */
  private void book(long technicianId, long bayId, Instant start, Instant end, String status) {
    fixtures.insertAppointment(
        dealershipId, otherVehicleId, serviceTypeId, technicianId, bayId, start, end, status);
  }

  private void bookVehicle(Instant start, Instant end, String status) {
    fixtures.insertAppointment(
        otherDealershipId,
        vehicleId,
        serviceTypeId,
        otherDealershipTechnicianId,
        otherDealershipBayId,
        start,
        end,
        status);
  }
}
