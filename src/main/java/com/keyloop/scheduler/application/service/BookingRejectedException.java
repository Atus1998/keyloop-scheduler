package com.keyloop.scheduler.application.service;

import com.keyloop.scheduler.domain.Reason;
import java.util.Objects;
import java.util.OptionalLong;

/** The booking was refused for {@link #reason()}. Nothing was stored. */
public final class BookingRejectedException extends RuntimeException {

  private final Reason reason;
  private final OptionalLong id;

  public BookingRejectedException(Reason reason) {
    this(reason, OptionalLong.empty());
  }

  /** A reference that was not found; {@code id} is the unknown id. */
  public BookingRejectedException(Reason reason, long id) {
    this(reason, OptionalLong.of(id));
  }

  private BookingRejectedException(Reason reason, OptionalLong id) {
    super("Booking rejected: " + Objects.requireNonNull(reason, "reason"));
    this.reason = reason;
    this.id = id;
  }

  public Reason reason() {
    return reason;
  }

  /** The unknown id for a not-found reason, else empty. */
  public OptionalLong id() {
    return id;
  }
}
