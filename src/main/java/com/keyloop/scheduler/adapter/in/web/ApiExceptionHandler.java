package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.application.service.AppointmentNotFoundException;
import com.keyloop.scheduler.application.service.BookingQueueTimeoutException;
import com.keyloop.scheduler.application.service.BookingRejectedException;
import com.keyloop.scheduler.application.service.ResourceConflictException;
import com.keyloop.scheduler.domain.Reason;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into a ProblemDetail (RFC 9457). Business errors carry a top-level {@code
 * reason}. Standard MVC errors (405, unknown path) come from {@link ResponseEntityExceptionHandler}
 * and carry none.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  static final String UNEXPECTED_DETAIL = "An unexpected error occurred.";
  static final String NO_RESOURCE_DETAIL = "No resource exists at this path.";
  static final String UNSUPPORTED_MEDIA_TYPE_DETAIL =
      "The request body must be sent as application/json.";
  static final String UNREADABLE_BODY_DETAIL =
      "The request body must be a JSON object with fields of the right type.";

  /** Long enough for a busy line of bookings to clear. */
  private static final String RETRY_AFTER_SECONDS = "1";

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(AppointmentNotFoundException.class)
  ProblemDetail handleNotFound(AppointmentNotFoundException e) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Appointment " + e.id() + " was not found.",
        Reason.APPOINTMENT_NOT_FOUND);
  }

  @ExceptionHandler(BookingRejectedException.class)
  ProblemDetail handleBookingRejected(BookingRejectedException e) {
    Reason reason = e.reason();
    return problem(ReasonProblems.status(reason), ReasonProblems.detail(reason, e.id()), reason);
  }

  /**
   * The booking waited too long for others of its dealership and day. Logged without the cause: it
   * holds SQL text.
   */
  @ExceptionHandler(BookingQueueTimeoutException.class)
  ResponseEntity<ProblemDetail> handleBookingQueueTimeout(BookingQueueTimeoutException e) {
    log.warn("Booking queue wait timed out");
    Reason reason = Reason.BOOKING_QUEUE_TIMEOUT;
    HttpStatus status = ReasonProblems.status(reason);
    return ResponseEntity.status(status)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(problem(status, ReasonProblems.detail(reason, OptionalLong.empty()), reason));
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail handleInvalidRequest(InvalidRequestException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), Reason.INVALID_REQUEST);
  }

  /**
   * A lost race that the booking flow did not resolve. Logged without the cause: its SQL text and
   * constraint names add nothing once the kind is known.
   */
  @ExceptionHandler(ResourceConflictException.class)
  ProblemDetail handleUnresolvedConflict(ResourceConflictException e) {
    log.warn("Unresolved resource conflict: kind={}", e.kind());
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_DETAIL);
  }

  /** Spring's default detail repeats the request path. Use a fixed one. */
  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, NO_RESOURCE_DETAIL);
    return handleExceptionInternal(e, body, headers, status, request);
  }

  /** Spring's default detail repeats the client's Content-Type. Use a fixed one. */
  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, UNSUPPORTED_MEDIA_TYPE_DETAIL);
    return handleExceptionInternal(e, body, headers, status, request);
  }

  /** Bad JSON or a wrong type. The detail is fixed: the parser message names Java types. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body =
        problem(HttpStatus.BAD_REQUEST, UNREADABLE_BODY_DETAIL, Reason.INVALID_REQUEST);
    return handleExceptionInternal(e, body, headers, status, request);
  }

  /** A missing or invalid field. The detail names the fields, never their values. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String fields =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));
    ProblemDetail body =
        problem(
            HttpStatus.BAD_REQUEST,
            "These fields are missing or invalid: " + fields + ".",
            Reason.INVALID_REQUEST);
    return handleExceptionInternal(e, body, headers, status, request);
  }

  /** Last resort. The body never carries the exception message: it can hold SQL or ids. */
  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception e) {
    log.error("Unexpected error", e);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_DETAIL);
  }

  private static ProblemDetail problem(HttpStatus status, String detail, Reason reason) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("reason", reason.name());
    return problem;
  }
}
