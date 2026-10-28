package com.keyloop.scheduler.application.port.in;

import com.keyloop.scheduler.domain.Appointment;

public interface GetAppointmentUseCase {

  /**
   * Returns the stored appointment with this id, whatever its status.
   *
   * @throws com.keyloop.scheduler.application.service.AppointmentNotFoundException when no
   *     appointment has this id
   */
  Appointment get(long id);
}
