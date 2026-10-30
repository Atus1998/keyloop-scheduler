package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Body of POST /appointments. {@code startTime} is a string parsed here: Jackson's date reader also
 * takes epoch numbers, which are not ISO-8601 with an offset.
 */
record BookAppointmentRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "1") @NotNull @Positive
        Long dealershipId,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "42") @NotNull @Positive Long vehicleId,
    @Schema(requiredMode = RequiredMode.REQUIRED, example = "3") @NotNull @Positive
        Long serviceTypeId,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            description = "ISO-8601 date-time with an offset.",
            example = "2026-11-03T09:00:00+07:00")
        @NotNull
        @Size(max = MAX_START_TIME_LENGTH)
        String startTime) {

  /** Longer than any valid ISO-8601 date-time with an offset and nanoseconds. */
  static final int MAX_START_TIME_LENGTH = 64;

  static final String INVALID_START_DETAIL =
      "startTime must be an ISO-8601 date-time with an offset, for example"
          + " 2026-11-03T09:00:00+07:00.";

  BookAppointmentCommand toCommand() {
    return new BookAppointmentCommand(dealershipId, vehicleId, serviceTypeId, parseStart());
  }

  private Instant parseStart() {
    try {
      return OffsetDateTime.parse(startTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
    } catch (DateTimeParseException e) {
      throw new InvalidRequestException(INVALID_START_DETAIL);
    }
  }
}
