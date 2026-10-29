package com.keyloop.scheduler.application.port.in;

public interface BookAppointmentUseCase {

  /**
   * Books a technician and a bay for the vehicle and returns the stored appointment. When the same
   * booking (dealership, vehicle, start, service type) is already {@code CONFIRMED}, returns that
   * appointment as a retry instead.
   *
   * @throws com.keyloop.scheduler.application.service.BookingRejectedException when a booking rule
   *     fails or nothing is free
   */
  BookingResult book(BookAppointmentCommand command);
}
