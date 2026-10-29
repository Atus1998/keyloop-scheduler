package com.keyloop.scheduler.application.port.in;

import com.keyloop.scheduler.domain.Appointment;
import java.util.Objects;

/**
 * The appointment a booking returns. {@code created} is false when the same booking was already
 * {@code CONFIRMED} and this request is a retry ({@code docs/system-design.md}, section 2).
 */
public record BookingResult(Appointment appointment, boolean created) {

  public BookingResult {
    Objects.requireNonNull(appointment, "appointment");
  }

  public static BookingResult created(Appointment appointment) {
    return new BookingResult(appointment, true);
  }

  public static BookingResult retried(Appointment appointment) {
    return new BookingResult(appointment, false);
  }
}
