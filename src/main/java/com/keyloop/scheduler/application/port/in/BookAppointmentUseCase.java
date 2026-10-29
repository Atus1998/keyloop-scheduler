package com.keyloop.scheduler.application.port.in;

import com.keyloop.scheduler.domain.Appointment;

public interface BookAppointmentUseCase {

  /**
   * Books a technician and a bay for the vehicle and returns the stored appointment.
   *
   * @throws com.keyloop.scheduler.application.service.BookingRejectedException when a booking rule
   *     fails or nothing is free
   */
  Appointment book(BookAppointmentCommand command);
}
