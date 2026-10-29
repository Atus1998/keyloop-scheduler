package com.keyloop.scheduler.application.port.out;

import com.keyloop.scheduler.domain.Appointment;
import java.time.Instant;
import java.util.Optional;

public interface AppointmentPort {

  /**
   * Stores a new appointment and returns it with its id.
   *
   * @throws com.keyloop.scheduler.application.service.ResourceConflictException when a CONFIRMED
   *     appointment overlaps on the technician, bay or vehicle, or the insert hit a deadlock
   */
  Appointment insert(Appointment appointment);

  /** Returns the appointment with this id, whatever its status. */
  Optional<Appointment> findById(long id);

  /** Returns the {@code CONFIRMED} appointment with exactly these fields, if there is one. */
  Optional<Appointment> findConfirmedRetryMatch(
      long dealershipId, long vehicleId, long serviceTypeId, Instant start);
}
