package com.keyloop.scheduler.application.service;

/** No appointment has the requested id. */
public final class AppointmentNotFoundException extends RuntimeException {

  private final long id;

  public AppointmentNotFoundException(long id) {
    super("Appointment not found: id=" + id);
    this.id = id;
  }

  public long id() {
    return id;
  }
}
