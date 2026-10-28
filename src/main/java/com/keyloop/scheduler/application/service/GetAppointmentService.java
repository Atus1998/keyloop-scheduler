package com.keyloop.scheduler.application.service;

import com.keyloop.scheduler.application.port.in.GetAppointmentUseCase;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.domain.Appointment;
import java.util.Objects;

public final class GetAppointmentService implements GetAppointmentUseCase {

  private final AppointmentPort appointments;

  public GetAppointmentService(AppointmentPort appointments) {
    this.appointments = Objects.requireNonNull(appointments, "appointments");
  }

  @Override
  public Appointment get(long id) {
    return appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id));
  }
}
