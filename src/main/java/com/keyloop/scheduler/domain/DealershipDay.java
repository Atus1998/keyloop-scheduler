package com.keyloop.scheduler.domain;

import java.time.Instant;
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

  public static DealershipDay containing(Instant instant, ZoneId zone) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
