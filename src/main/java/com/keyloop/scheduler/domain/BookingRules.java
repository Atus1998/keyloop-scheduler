package com.keyloop.scheduler.domain;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Booking rules from {@code docs/system-design.md}, section 2. {@link #check} returns the first
 * rule that fails, in the order of the booking checks table.
 */
public final class BookingRules {

  public static final int MAX_DAYS_AHEAD = 90;

  private BookingRules() {}

  public static Optional<Reason> check(
      Dealership dealership,
      ServiceType serviceType,
      List<Technician> qualifiedTechnicians,
      Instant start,
      Instant now) {
    Objects.requireNonNull(dealership, "dealership");
    Objects.requireNonNull(serviceType, "serviceType");
    Objects.requireNonNull(qualifiedTechnicians, "qualifiedTechnicians");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(now, "now");

    if (!isWholeMinute(start)) {
      return Optional.of(Reason.START_NOT_WHOLE_MINUTE);
    }
    if (!start.isAfter(now)) {
      return Optional.of(Reason.START_IN_PAST);
    }
    if (isTooFarAhead(dealership.zoneId(), start, now)) {
      return Optional.of(Reason.START_TOO_FAR_AHEAD);
    }
    if (!fitsOpeningHours(dealership, start, start.plus(serviceType.duration()))) {
      return Optional.of(Reason.OUTSIDE_OPENING_HOURS);
    }
    if (!isOffered(dealership, qualifiedTechnicians)) {
      return Optional.of(Reason.SERVICE_NOT_OFFERED);
    }
    return Optional.empty();
  }

  /** Turns the two picker results into a 409 reason, or empty when both resources are free. */
  public static Optional<Reason> availability(OptionalLong technicianId, OptionalLong bayId) {
    Objects.requireNonNull(technicianId, "technicianId");
    Objects.requireNonNull(bayId, "bayId");

    if (technicianId.isEmpty() && bayId.isEmpty()) {
      return Optional.of(Reason.BUSY);
    }
    if (technicianId.isEmpty()) {
      return Optional.of(Reason.NO_TECHNICIAN);
    }
    if (bayId.isEmpty()) {
      return Optional.of(Reason.NO_BAY);
    }
    return Optional.empty();
  }

  private static boolean isWholeMinute(Instant start) {
    return start.truncatedTo(MINUTES).equals(start);
  }

  /** Counts days on the dealership calendar, so day 90 is allowed and day 91 is not. */
  private static boolean isTooFarAhead(ZoneId zone, Instant start, Instant now) {
    LocalDate today = now.atZone(zone).toLocalDate();
    LocalDate startDate = start.atZone(zone).toLocalDate();
    return DAYS.between(today, startDate) > MAX_DAYS_AHEAD;
  }

  /** Builds open and close as instants on the local start date, so clock-change days work. */
  private static boolean fitsOpeningHours(Dealership dealership, Instant start, Instant end) {
    ZoneId zone = dealership.zoneId();
    LocalDate date = start.atZone(zone).toLocalDate();
    Instant open = date.atTime(dealership.opensAt()).atZone(zone).toInstant();
    Instant close = date.atTime(dealership.closesAt()).atZone(zone).toInstant();
    return !start.isBefore(open) && !end.isAfter(close);
  }

  private static boolean isOffered(Dealership dealership, List<Technician> qualifiedTechnicians) {
    return qualifiedTechnicians.stream()
        .anyMatch(technician -> technician.dealershipId() == dealership.id());
  }
}
