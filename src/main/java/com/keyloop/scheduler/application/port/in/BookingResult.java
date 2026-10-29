package com.keyloop.scheduler.application.port.in;

import com.keyloop.scheduler.domain.Appointment;
import java.util.Objects;

/**
 * The appointment a booking returns. {@code created} is false when the same booking was already
 * {@code CONFIRMED} and this request is a retry ({@code docs/system-design.md}, section 2). Built
 * only through {@link #created(Appointment)} and {@link #retried(Appointment)}.
 */
public final class BookingResult {

  private final Appointment appointment;
  private final boolean created;

  private BookingResult(Appointment appointment, boolean created) {
    this.appointment = Objects.requireNonNull(appointment, "appointment");
    this.created = created;
  }

  public static BookingResult created(Appointment appointment) {
    return new BookingResult(appointment, true);
  }

  public static BookingResult retried(Appointment appointment) {
    return new BookingResult(appointment, false);
  }

  public Appointment appointment() {
    return appointment;
  }

  public boolean created() {
    return created;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BookingResult that
        && created == that.created
        && appointment.equals(that.appointment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(appointment, created);
  }

  @Override
  public String toString() {
    return "BookingResult[appointment=" + appointment + ", created=" + created + "]";
  }
}
