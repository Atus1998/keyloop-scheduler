package com.keyloop.scheduler.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** One day on a dealership calendar, as the instants {@code [start, end)}. */
public record DealershipDay(Instant start, Instant end) {

  public DealershipDay {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("A day must end after it starts: " + start + "-" + end);
    }
  }

  /**
   * The local day that holds {@code instant}. It lasts 23 or 25 hours on clock-change days, and
   * starts at the first valid local time when midnight is skipped.
   */
  public static DealershipDay containing(Instant instant, ZoneId zone) {
    Objects.requireNonNull(instant, "instant");
    Objects.requireNonNull(zone, "zone");
    LocalDate date = dateOf(instant, zone);
    return new DealershipDay(
        date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
  }

  /** The local date of {@code instant} on the dealership calendar. */
  public static LocalDate dateOf(Instant instant, ZoneId zone) {
    Objects.requireNonNull(instant, "instant");
    Objects.requireNonNull(zone, "zone");
    return instant.atZone(zone).toLocalDate();
  }
}
