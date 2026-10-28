package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.domain.Appointment;
import java.time.Instant;

/** Body of a stored appointment. Times are UTC instants, written as {@code ...Z}. */
record AppointmentResponse(
    long id,
    String status,
    long dealershipId,
    long vehicleId,
    long serviceTypeId,
    long technicianId,
    long bayId,
    Instant startTime,
    Instant endTime) {

  static AppointmentResponse from(Appointment appointment) {
    return new AppointmentResponse(
        appointment.id(),
        appointment.status().name(),
        appointment.dealershipId(),
        appointment.vehicleId(),
        appointment.serviceTypeId(),
        appointment.technicianId(),
        appointment.bayId(),
        appointment.startAt(),
        appointment.endAt());
  }
}
