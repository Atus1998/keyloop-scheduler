package com.keyloop.scheduler.application.service;

/** A booking waited too long for the other bookings of its dealership and day. */
public final class BookingQueueTimeoutException extends RuntimeException {

  public BookingQueueTimeoutException(Throwable cause) {
    super("Timed out waiting for the dealership and day", cause);
  }
}
