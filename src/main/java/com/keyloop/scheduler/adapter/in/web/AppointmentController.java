package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.application.port.in.BookingResult;
import com.keyloop.scheduler.application.port.in.GetAppointmentUseCase;
import com.keyloop.scheduler.domain.Appointment;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AppointmentController {

  static final String INVALID_ID_DETAIL = "The appointment id must be a positive whole number.";

  private final GetAppointmentUseCase getAppointment;
  private final BookAppointmentUseCase bookAppointment;

  AppointmentController(
      GetAppointmentUseCase getAppointment, BookAppointmentUseCase bookAppointment) {
    this.getAppointment = getAppointment;
    this.bookAppointment = bookAppointment;
  }

  @PostMapping("/appointments")
  ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
    BookingResult result = bookAppointment.book(request.toCommand());
    Appointment booked = result.appointment();
    AppointmentResponse body = AppointmentResponse.from(booked);
    if (!result.created()) {
      return ResponseEntity.ok(body);
    }
    return ResponseEntity.created(URI.create("/appointments/" + booked.id())).body(body);
  }

  @GetMapping("/appointments/{id}")
  AppointmentResponse get(@PathVariable String id) {
    return AppointmentResponse.from(getAppointment.get(parseId(id)));
  }

  /**
   * Accepts ASCII digits only. A {@code long} path variable would go through {@link Long#decode},
   * which also takes {@code 0x10}, {@code +5}, {@code -5} and non-ASCII digits.
   */
  static long parseId(String raw) {
    if (raw.isEmpty() || !raw.chars().allMatch(c -> c >= '0' && c <= '9')) {
      throw new InvalidRequestException(INVALID_ID_DETAIL);
    }
    long id;
    try {
      id = Long.parseLong(raw);
    } catch (NumberFormatException tooLarge) {
      throw new InvalidRequestException(INVALID_ID_DETAIL);
    }
    if (id == 0) {
      throw new InvalidRequestException(INVALID_ID_DETAIL);
    }
    return id;
  }
}
