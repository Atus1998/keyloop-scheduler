package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.domain.Appointment;
import org.springframework.stereotype.Component;

@Component
class AppointmentAdapter implements AppointmentPort {

  @Override
  public Appointment insert(Appointment appointment) {
    throw new UnsupportedOperationException("not implemented");
  }

  static RuntimeException translate(RuntimeException error) {
    return error;
  }
}
