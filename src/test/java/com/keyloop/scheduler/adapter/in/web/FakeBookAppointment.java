package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.application.port.in.BookingResult;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Records each command. By default it books appointment 101 with technician 7 and bay 2. With
 * {@code retry} set, it reports the answer as an existing booking.
 */
final class FakeBookAppointment implements BookAppointmentUseCase {

  static final long BOOKED_ID = 101;

  final List<BookAppointmentCommand> commands = new ArrayList<>();
  Function<BookAppointmentCommand, Appointment> answer;
  boolean retry;

  FakeBookAppointment() {
    reset();
  }

  void reset() {
    commands.clear();
    answer = FakeBookAppointment::booked;
    retry = false;
  }

  @Override
  public BookingResult book(BookAppointmentCommand command) {
    commands.add(command);
    Appointment appointment = answer.apply(command);
    return retry ? BookingResult.retried(appointment) : BookingResult.created(appointment);
  }

  static Appointment booked(BookAppointmentCommand command) {
    return new Appointment(
        BOOKED_ID,
        command.dealershipId(),
        command.vehicleId(),
        command.serviceTypeId(),
        7,
        2,
        command.startTime(),
        command.startTime().plus(Duration.ofMinutes(90)),
        AppointmentStatus.CONFIRMED);
  }
}
