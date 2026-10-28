package com.keyloop.scheduler.adapter.in.web;

import com.keyloop.scheduler.application.service.AppointmentNotFoundException;
import com.keyloop.scheduler.domain.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into a ProblemDetail (RFC 9457). Business errors carry a top-level {@code
 * reason}. Standard MVC errors (405, unknown path) come from {@link ResponseEntityExceptionHandler}
 * and carry none.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  static final String UNEXPECTED_DETAIL = "An unexpected error occurred.";

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(AppointmentNotFoundException.class)
  ProblemDetail handleNotFound(AppointmentNotFoundException e) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Appointment " + e.id() + " was not found.",
        Reason.APPOINTMENT_NOT_FOUND);
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail handleInvalidRequest(InvalidRequestException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), Reason.INVALID_REQUEST);
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
