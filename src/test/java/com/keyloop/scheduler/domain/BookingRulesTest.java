package com.keyloop.scheduler.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The test JVM runs in Asia/Ho_Chi_Minh, so no dealership here uses that zone. */
class BookingRulesTest {

  private static final ServiceType ONE_HOUR =
      new ServiceType(1, "Tyre rotation", Duration.ofMinutes(60));
  private static final Dealership LONDON =
      new Dealership(
          1, "London", ZoneId.of("Europe/London"), LocalTime.of(9, 0), LocalTime.of(18, 0));
  private static final Dealership TOKYO =
      new Dealership(2, "Tokyo", ZoneId.of("Asia/Tokyo"), LocalTime.of(8, 0), LocalTime.of(18, 0));
  private static final Dealership BOGOTA =
      new Dealership(
          3, "Bogota", ZoneId.of("America/Bogota"), LocalTime.of(8, 0), LocalTime.of(18, 0));

  private static Optional<Reason> check(Dealership dealership, String start, String now) {
    return check(dealership, localTechnician(dealership), start, now);
  }

  private static Optional<Reason> check(
      Dealership dealership, List<Technician> technicians, String start, String now) {
    return BookingRules.check(
        dealership, ONE_HOUR, technicians, Instant.parse(start), Instant.parse(now));
  }

  private static List<Technician> localTechnician(Dealership dealership) {
    return List.of(new Technician(10, dealership.id(), "Ann"));
  }

  // Whole minute

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-11-02T10:00:30Z",
        "2026-11-02T10:00:00.000001Z",
        "2026-11-02T10:00:00.000000001Z"
      })
  void returnsStartNotWholeMinuteWhenStartHasSecondsOrFraction(String start) {
    assertThat(check(LONDON, start, "2026-11-02T00:00:00Z"))
        .contains(Reason.START_NOT_WHOLE_MINUTE);
  }

  @Test
  void allowsStartOnWholeMinute() {
    assertThat(check(LONDON, "2026-11-02T10:00:00Z", "2026-11-02T00:00:00Z")).isEmpty();
  }

  // In the past

  @Test
  void returnsStartInPastWhenStartEqualsNow() {
    assertThat(check(LONDON, "2026-11-02T10:00:00Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.START_IN_PAST);
  }

  @Test
  void returnsStartInPastWhenStartIsBeforeNow() {
    assertThat(check(LONDON, "2026-11-02T09:59:00Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.START_IN_PAST);
  }

  @Test
  void allowsStartOneMinuteAfterNow() {
    assertThat(check(LONDON, "2026-11-02T10:01:00Z", "2026-11-02T10:00:00Z")).isEmpty();
  }

  // 90 days ahead

  @Test
  void allowsStartOnDay90() {
    assertThat(check(LONDON, "2027-01-31T10:00:00Z", "2026-11-02T10:00:00Z")).isEmpty();
  }

  @Test
  void returnsStartTooFarAheadOnDay91() {
    assertThat(check(LONDON, "2027-02-01T10:00:00Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.START_TOO_FAR_AHEAD);
  }

  @Test
  void allowsDay90OnLocalCalendarWhenUtcDatesSay91() {
    // Now is 2026-10-28 03:00 in Tokyo; start is 2027-01-26 09:00 in Tokyo.
    assertThat(check(TOKYO, "2027-01-26T00:00:00Z", "2026-10-27T18:00:00Z")).isEmpty();
  }

  @Test
  void rejectsDay91OnLocalCalendarInTokyo() {
    assertThat(check(TOKYO, "2027-01-27T00:00:00Z", "2026-10-27T18:00:00Z"))
        .contains(Reason.START_TOO_FAR_AHEAD);
  }

  @Test
  void rejectsDay91OnLocalCalendarWhenUtcDatesSay90() {
    // Now is 2026-10-27 21:00 in Bogota; start is 2027-01-26 09:00 in Bogota.
    assertThat(check(BOGOTA, "2027-01-26T14:00:00Z", "2026-10-28T02:00:00Z"))
        .contains(Reason.START_TOO_FAR_AHEAD);
  }

  @Test
  void allowsDay90OnLocalCalendarInBogota() {
    assertThat(check(BOGOTA, "2027-01-25T14:00:00Z", "2026-10-28T02:00:00Z")).isEmpty();
  }

  // Opening hours: London opens 09:00 to 18:00 local time

  @ParameterizedTest
  @ValueSource(strings = {"2026-03-29T08:00:00Z", "2026-03-29T16:00:00Z"})
  void allowsStartAtOpeningAndEndAtClosingOnSpringForwardDay(String start) {
    assertThat(check(LONDON, start, "2026-03-01T00:00:00Z")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-03-29T07:59:00Z", "2026-03-29T16:01:00Z"})
  void returnsOutsideOpeningHoursOnSpringForwardDay(String start) {
    assertThat(check(LONDON, start, "2026-03-01T00:00:00Z")).contains(Reason.OUTSIDE_OPENING_HOURS);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-25T09:00:00Z", "2026-10-25T17:00:00Z"})
  void allowsStartAtOpeningAndEndAtClosingOnFallBackDay(String start) {
    assertThat(check(LONDON, start, "2026-10-01T00:00:00Z")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-25T08:59:00Z", "2026-10-25T17:01:00Z"})
  void returnsOutsideOpeningHoursOnFallBackDay(String start) {
    assertThat(check(LONDON, start, "2026-10-01T00:00:00Z")).contains(Reason.OUTSIDE_OPENING_HOURS);
  }

  @Test
  void usesWinterOffsetTheDayBeforeSpringForward() {
    // 08:00Z is 08:00 local on 2026-03-28, one hour before opening.
    assertThat(check(LONDON, "2026-03-28T08:00:00Z", "2026-03-01T00:00:00Z"))
        .contains(Reason.OUTSIDE_OPENING_HOURS);
  }

  @Test
  void usesSummerOffsetTheDayBeforeFallBack() {
    // 08:00Z is 09:00 local on 2026-10-24, the opening time.
    assertThat(check(LONDON, "2026-10-24T08:00:00Z", "2026-10-01T00:00:00Z")).isEmpty();
  }

  @Test
  void returnsOutsideOpeningHoursWhenServiceRunsPastClosing() {
    // 17:30 to 18:30 in Tokyo.
    assertThat(check(TOKYO, "2026-11-02T08:30:00Z", "2026-11-01T00:00:00Z"))
        .contains(Reason.OUTSIDE_OPENING_HOURS);
  }

  @Test
  void returnsOutsideOpeningHoursLateAtNight() {
    // 23:00 in Tokyo.
    assertThat(check(TOKYO, "2026-11-02T14:00:00Z", "2026-11-01T00:00:00Z"))
        .contains(Reason.OUTSIDE_OPENING_HOURS);
  }

  // Service offered

  @Test
  void returnsServiceNotOfferedWhenNoQualifiedTechnician() {
    assertThat(check(LONDON, List.of(), "2026-11-02T10:00:00Z", "2026-11-02T00:00:00Z"))
        .contains(Reason.SERVICE_NOT_OFFERED);
  }

  @Test
  void returnsServiceNotOfferedWhenOnlyTechniciansOfOtherDealerships() {
    var elsewhere = List.of(new Technician(20, 99, "Bob"));

    assertThat(check(LONDON, elsewhere, "2026-11-02T10:00:00Z", "2026-11-02T00:00:00Z"))
        .contains(Reason.SERVICE_NOT_OFFERED);
  }

  @Test
  void allowsBookingWhenDealershipHasQualifiedTechnician() {
    assertThat(check(LONDON, "2026-11-02T10:00:00Z", "2026-11-02T00:00:00Z")).isEmpty();
  }

  // Order of rules

  @Test
  void wholeMinuteRuleWinsOverStartInPast() {
    assertThat(check(LONDON, "2026-11-02T09:59:30Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.START_NOT_WHOLE_MINUTE);
  }

  @Test
  void startInPastWinsOverOpeningHours() {
    assertThat(check(LONDON, "2026-11-02T03:00:00Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.START_IN_PAST);
  }

  @Test
  void tooFarAheadWinsOverOpeningHours() {
    assertThat(check(LONDON, "2027-02-01T03:00:00Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.START_TOO_FAR_AHEAD);
  }

  @Test
  void openingHoursWinOverServiceNotOffered() {
    assertThat(check(LONDON, List.of(), "2026-11-03T03:00:00Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.OUTSIDE_OPENING_HOURS);
  }

  // Availability

  @Test
  void availabilityPassesWhenTechnicianAndBayAreFree() {
    assertThat(BookingRules.availability(OptionalLong.of(1), OptionalLong.of(2))).isEmpty();
  }

  @Test
  void returnsNoTechnicianWhenOnlyTechnicianIsMissing() {
    assertThat(BookingRules.availability(OptionalLong.empty(), OptionalLong.of(2)))
        .contains(Reason.NO_TECHNICIAN);
  }

  @Test
  void returnsNoBayWhenOnlyBayIsMissing() {
    assertThat(BookingRules.availability(OptionalLong.of(1), OptionalLong.empty()))
        .contains(Reason.NO_BAY);
  }

  @Test
  void returnsBusyWhenTechnicianAndBayAreMissing() {
    assertThat(BookingRules.availability(OptionalLong.empty(), OptionalLong.empty()))
        .contains(Reason.BUSY);
  }

  // Null arguments

  @Test
  void checkRejectsNullArgumentsByName() {
    var technicians = localTechnician(LONDON);
    var start = Instant.parse("2026-11-02T10:00:00Z");
    var now = Instant.parse("2026-11-02T00:00:00Z");

    assertThatNullPointerException()
        .isThrownBy(() -> BookingRules.check(null, ONE_HOUR, technicians, start, now))
        .withMessage("dealership");
    assertThatNullPointerException()
        .isThrownBy(() -> BookingRules.check(LONDON, null, technicians, start, now))
        .withMessage("serviceType");
    assertThatNullPointerException()
        .isThrownBy(() -> BookingRules.check(LONDON, ONE_HOUR, null, start, now))
        .withMessage("qualifiedTechnicians");
    assertThatNullPointerException()
        .isThrownBy(() -> BookingRules.check(LONDON, ONE_HOUR, technicians, null, now))
        .withMessage("start");
    assertThatNullPointerException()
        .isThrownBy(() -> BookingRules.check(LONDON, ONE_HOUR, technicians, start, null))
        .withMessage("now");
  }

  @Test
  void availabilityRejectsNullArgumentsByName() {
    assertThatNullPointerException()
        .isThrownBy(() -> BookingRules.availability(null, OptionalLong.of(2)))
        .withMessage("technicianId");
    assertThatNullPointerException()
        .isThrownBy(() -> BookingRules.availability(OptionalLong.of(1), null))
        .withMessage("bayId");
  }

  // Edge cases: extreme starts, sub-minute now, clock-change hours, null elements

  @ParameterizedTest
  @ValueSource(strings = {"+999999999-12-31T23:59:00Z", "+1000000000-12-31T23:59:00Z"})
  void returnsStartTooFarAheadForStartNearInstantMax(String start) {
    assertThat(check(TOKYO, start, "2026-11-02T00:00:00Z")).contains(Reason.START_TOO_FAR_AHEAD);
    assertThat(check(LONDON, start, "2026-11-02T00:00:00Z")).contains(Reason.START_TOO_FAR_AHEAD);
  }

  @Test
  void allowsWholeMinuteStartJustAfterNowWithSeconds() {
    assertThat(check(LONDON, "2026-11-02T10:01:00Z", "2026-11-02T10:00:30Z")).isEmpty();
  }

  @Test
  void returnsStartInPastWhenStartIsTheMinuteBeforeNowWithSeconds() {
    assertThat(check(LONDON, "2026-11-02T10:00:00Z", "2026-11-02T10:00:30Z"))
        .contains(Reason.START_IN_PAST);
  }

  @Test
  void startInPastWinsWhenThreeRulesFail() {
    // In the past, outside opening hours and no qualified technician.
    assertThat(check(LONDON, List.of(), "2026-11-02T03:00:00Z", "2026-11-02T10:00:00Z"))
        .contains(Reason.START_IN_PAST);
  }

  @Test
  void openingTimeInsideSkippedHourMovesForwardByTheGap() {
    // 01:30 does not exist in London on 2026-03-29, so opening is 02:30 BST (01:30Z).
    var earlyOpening =
        new Dealership(
            4, "Early", ZoneId.of("Europe/London"), LocalTime.of(1, 30), LocalTime.of(18, 0));

    assertThat(check(earlyOpening, "2026-03-29T01:00:00Z", "2026-03-01T00:00:00Z"))
        .contains(Reason.OUTSIDE_OPENING_HOURS);
    assertThat(check(earlyOpening, "2026-03-29T01:30:00Z", "2026-03-01T00:00:00Z")).isEmpty();
  }

  @Test
  void openingTimeInsideRepeatedHourUsesEarlierOffset() {
    // 01:30 happens twice in London on 2026-10-25; the first one is 01:30 BST (00:30Z).
    var earlyOpening =
        new Dealership(
            4, "Early", ZoneId.of("Europe/London"), LocalTime.of(1, 30), LocalTime.of(18, 0));

    assertThat(check(earlyOpening, "2026-10-25T00:29:00Z", "2026-10-01T00:00:00Z"))
        .contains(Reason.OUTSIDE_OPENING_HOURS);
    assertThat(check(earlyOpening, "2026-10-25T00:30:00Z", "2026-10-01T00:00:00Z")).isEmpty();
  }

  @Test
  void checkRejectsNullTechnicianInList() {
    var technicians = Arrays.asList(new Technician(10, LONDON.id(), "Ann"), null);

    assertThatNullPointerException()
        .isThrownBy(
            () -> check(LONDON, technicians, "2026-11-02T10:00:00Z", "2026-11-02T00:00:00Z"))
        .withMessage("qualifiedTechnicians contains null");
  }
}
