package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.application.port.in.BookingResult;
import com.keyloop.scheduler.application.port.in.GetAppointmentUseCase;
import com.keyloop.scheduler.domain.Appointment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Error responses are documented in {@link OpenApiConfig}. */
@RestController
@Tag(name = "Appointments")
class AppointmentController {

  static final String INVALID_ID_DETAIL = "The appointment id must be a positive whole number.";

  private final GetAppointmentUseCase getAppointment;
  private final BookAppointmentUseCase bookAppointment;

  AppointmentController(
      GetAppointmentUseCase getAppointment, BookAppointmentUseCase bookAppointment) {
    this.getAppointment = getAppointment;
    this.bookAppointment = bookAppointment;
  }

  @Operation(
      summary = "Book a service appointment",
      description =
          "Picks a free qualified technician and a free service bay for the whole service."
              + " Sending the same booking again returns the stored appointment with 200.")
  @ApiResponse(
      responseCode = "201",
      description = "Booked.",
      headers =
          @Header(
              name = "Location",
              description = "Path of the new appointment.",
              schema = @Schema(type = "string", example = "/appointments/101")),
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = AppointmentResponse.class)))
  @ApiResponse(
      responseCode = "200",
      description =
          "Retry: a confirmed appointment with the same dealership, vehicle, service type and"
              + " start time already exists, so it is returned. No Location header.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = AppointmentResponse.class)))
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

  @Operation(summary = "Get an appointment")
  @ApiResponse(
      responseCode = "200",
      description = "The appointment.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = AppointmentResponse.class)))
  @GetMapping("/appointments/{id}")
  AppointmentResponse get(
      @Parameter(description = "Appointment id, a positive whole number.", example = "101")
          @PathVariable
          String id) {
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
