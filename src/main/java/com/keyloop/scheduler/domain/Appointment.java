package com.keyloop.scheduler.domain;

import java.time.Instant;
import java.util.Objects;

/** A booked appointment. {@code id} is null until the appointment is stored. */
public record Appointment(
    Long id,
    long dealershipId,
    long vehicleId,
    long serviceTypeId,
    long technicianId,
    long bayId,
    Instant startAt,
    Instant endAt,
    AppointmentStatus status) {

  public Appointment {
    Objects.requireNonNull(startAt, "startAt");
    Objects.requireNonNull(endAt, "endAt");
    Objects.requireNonNull(status, "status");
  }

  public Appointment withId(long newId) {
    return new Appointment(
        newId, dealershipId, vehicleId, serviceTypeId, technicianId, bayId, startAt, endAt, status);
  }
}
