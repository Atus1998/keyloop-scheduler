package com.keyloop.scheduler.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public final class BookingRules {

  public static final int MAX_DAYS_AHEAD = 90;

  private BookingRules() {}

  public static Optional<Reason> check(
      Dealership dealership,
      ServiceType serviceType,
      List<Technician> qualifiedTechnicians,
      Instant start,
      Instant now) {
    throw new UnsupportedOperationException("not implemented");
  }

  public static Optional<Reason> availability(OptionalLong technicianId, OptionalLong bayId) {
    throw new UnsupportedOperationException("not implemented");
  }
}
