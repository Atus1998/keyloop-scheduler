package com.keyloop.scheduler.domain;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** A dealership. Opening hours are local times in {@code zoneId}, the same every day. */
public record Dealership(
    long id, String name, ZoneId zoneId, LocalTime opensAt, LocalTime closesAt) {

  public Dealership {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(zoneId, "zoneId");
    Objects.requireNonNull(opensAt, "opensAt");
    Objects.requireNonNull(closesAt, "closesAt");
  }
}
