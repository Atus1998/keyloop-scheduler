package com.keyloop.scheduler.application.port.in;

import java.time.Instant;
import java.util.Objects;

/** A booking request with a valid shape: positive ids and a start instant. */
public record BookAppointmentCommand(
    long dealershipId, long vehicleId, long serviceTypeId, Instant startTime) {

  public BookAppointmentCommand {
    requirePositive(dealershipId, "dealershipId");
    requirePositive(vehicleId, "vehicleId");
    requirePositive(serviceTypeId, "serviceTypeId");
    Objects.requireNonNull(startTime, "startTime");
  }

  private static void requirePositive(long id, String name) {
    if (id <= 0) {
      throw new IllegalArgumentException(name + " must be positive: " + id);
    }
  }
}
