package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.domain.Reason;
import java.util.Locale;
import java.util.OptionalLong;
import org.springframework.http.HttpStatus;

/**
 * HTTP status and client-safe detail for each {@link Reason}. The switches name every reason, so a
 * new one does not compile until it is mapped.
 */
final class ReasonProblems {

  private ReasonProblems() {}

  static HttpStatus status(Reason reason) {
    return switch (reason) {
      case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
      case DEALERSHIP_NOT_FOUND, VEHICLE_NOT_FOUND, SERVICE_TYPE_NOT_FOUND, APPOINTMENT_NOT_FOUND ->
          HttpStatus.NOT_FOUND;
      case START_NOT_WHOLE_MINUTE,
          START_IN_PAST,
          START_TOO_FAR_AHEAD,
          OUTSIDE_OPENING_HOURS,
          SERVICE_NOT_OFFERED ->
          HttpStatus.UNPROCESSABLE_CONTENT;
      case NO_TECHNICIAN, NO_BAY, BUSY, VEHICLE_BUSY, TECHNICIAN_TAKEN, BAY_TAKEN, SLOT_TAKEN ->
          HttpStatus.CONFLICT;
      case BOOKING_QUEUE_TIMEOUT -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  /** {@code id} is the unknown id of a not-found reason; other reasons ignore it. */
  static String detail(Reason reason, OptionalLong id) {
    return switch (reason) {
      case INVALID_REQUEST -> "The request is not valid.";
      case DEALERSHIP_NOT_FOUND -> notFound("Dealership", id);
      case VEHICLE_NOT_FOUND -> notFound("Vehicle", id);
      case SERVICE_TYPE_NOT_FOUND -> notFound("Service type", id);
      case APPOINTMENT_NOT_FOUND -> notFound("Appointment", id);
      case START_NOT_WHOLE_MINUTE -> "The start time must be a whole minute, with no seconds.";
      case START_IN_PAST -> "The start time must be in the future.";
      case START_TOO_FAR_AHEAD -> "The start date must be at most 90 days ahead.";
      case OUTSIDE_OPENING_HOURS ->
          "The service must start and end within the dealership's opening hours.";
      case SERVICE_NOT_OFFERED -> "No technician at this dealership offers this service.";
      case NO_TECHNICIAN -> "No qualified technician is free at this time.";
      case NO_BAY -> "No service bay is free at this time.";
      case BUSY -> "No qualified technician and no service bay are free at this time.";
      case VEHICLE_BUSY -> "The vehicle already has an appointment at this time.";
      case TECHNICIAN_TAKEN ->
          "The technician was booked by another request. Send the request again.";
      case BAY_TAKEN -> "The bay was booked by another request. Send the request again.";
      case SLOT_TAKEN -> "The slot was booked by another request. Send the request again.";
      case BOOKING_QUEUE_TIMEOUT ->
          "Too many bookings for this dealership and day are in progress. Send the request again.";
    };
  }

  private static String notFound(String subject, OptionalLong id) {
    return id.isPresent()
        ? subject + " " + id.getAsLong() + " was not found."
        : "The " + subject.toLowerCase(Locale.ROOT) + " was not found.";
  }
}
