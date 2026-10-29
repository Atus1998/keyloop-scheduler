package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Records each command. By default it books appointment 101 with technician 7 and bay 2. */
final class FakeBookAppointment implements BookAppointmentUseCase {

  static final long BOOKED_ID = 101;

  final List<BookAppointmentCommand> commands = new ArrayList<>();
  Function<BookAppointmentCommand, Appointment> answer;

  FakeBookAppointment() {
    reset();
  }

  void reset() {
    commands.clear();
    answer = FakeBookAppointment::booked;
  }

  @Override
  public Appointment book(BookAppointmentCommand command) {
    commands.add(command);
    return answer.apply(command);
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
