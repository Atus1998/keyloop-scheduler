package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.application.port.in.GetAppointmentUseCase;
import com.keyloop.scheduler.application.service.AppointmentNotFoundException;
import com.keyloop.scheduler.application.service.ResourceConflictException;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/** Error mapping of the web adapter. No database: the use case is a hand-written fake. */
@WebMvcTest(AppointmentController.class)
@Import(ApiExceptionHandlerTest.FakeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

  private static final String INVALID_ID_DETAIL =
      "The appointment id must be a positive whole number.";
  private static final String UNEXPECTED_DETAIL = "An unexpected error occurred.";
  private static final String SECRET_CONSTRAINT = "appointment_technician_no_overlap";
  private static final String SECRET_SQL = "INSERT INTO appointment (technician_id) VALUES (7)";

  @Autowired private MockMvcTester mvc;
  @Autowired private FakeGetAppointment useCase;

  @BeforeEach
  void resetFake() {
    useCase.reset();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"abc", "0x10", "+5", "-5", "٣", "１２", "1 OR 1=1", "1.json", " 7", "7 ", "1e3"})
  void returns400WithoutCallingUseCaseWhenIdIsNotPlainDigits(String id) {
    assertInvalidId(mvc.get().uri("/appointments/{id}", id).exchange());
  }

  @Test
  void returns400ForPreEncodedSqlLikeId() {
    assertInvalidId(mvc.get().uri(URI.create("/appointments/1%20OR%201%3D1")).exchange());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "000", "9223372036854775808", "99999999999999999999"})
  void returns400WithoutCallingUseCaseWhenIdIsZeroOrTooLarge(String id) {
    assertInvalidId(mvc.get().uri("/appointments/{id}", id).exchange());
  }

  @Test
  void passesLeadingZeroIdAsNumber() {
    useCase.answer = ApiExceptionHandlerTest::appointment;

    assertThat(mvc.get().uri("/appointments/007")).hasStatusOk();
    assertThat(useCase.requestedIds).containsExactly(7L);
  }

  @Test
  void returns404WithReasonWhenUseCaseFindsNothing() {
    var result = mvc.get().uri("/appointments/7").exchange();

    assertThat(result)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("APPOINTMENT_NOT_FOUND");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("Appointment 7 was not found.");
  }

  @Test
  void returns500WithFixedDetailWhenUnexpectedErrorOccurs() {
    useCase.answer =
        id -> {
          throw new IllegalStateException(SECRET_CONSTRAINT + " " + SECRET_SQL);
        };

    assertUnexpectedError(mvc.get().uri("/appointments/7").exchange());
  }

  @Test
  void returns500WithoutSqlDetailWhenResourceConflictReachesWeb() {
    var sqlError =
        new SQLException(
            "ERROR: conflicting key value violates exclusion constraint \""
                + SECRET_CONSTRAINT
                + "\"",
            "23P01");
    useCase.answer =
        id -> {
          throw new ResourceConflictException(
              ResourceConflictException.Kind.EXCLUSION,
              new DataIntegrityViolationException(SECRET_SQL, sqlError));
        };

    assertUnexpectedError(mvc.get().uri("/appointments/7").exchange());
  }

  @Test
  void returns500WhenDatabaseIsUnreachable() {
    useCase.answer =
        id -> {
          throw new DataAccessResourceFailureException(
              "Connection to db:5432 refused " + SECRET_SQL);
        };

    assertUnexpectedError(mvc.get().uri("/appointments/7").exchange());
  }

  @Test
  void logsCauseOfUnexpectedError(CapturedOutput output) {
    useCase.answer =
        id -> {
          throw new IllegalStateException("boom " + SECRET_CONSTRAINT);
        };

    assertThat(mvc.get().uri("/appointments/7")).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(output).contains("IllegalStateException").contains("boom " + SECRET_CONSTRAINT);
  }

  @Test
  void returns405ProblemDetailForUnsupportedMethod() {
    var result = mvc.delete().uri("/appointments/7").exchange();

    assertThat(result)
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .containsHeader("Allow");
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(405);
    assertThat(result).bodyJson().doesNotHavePath("$.reason");
    assertThat(useCase.requestedIds).isEmpty();
  }

  @Test
  void returns404ProblemDetailForUnknownPath() {
    var result = mvc.get().uri("/no-such-path").exchange();

    assertThat(result)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
    assertThat(result).bodyJson().doesNotHavePath("$.reason");
  }

  @Test
  void unknownPathDetailDoesNotEchoPath() {
    var result = mvc.get().uri("/secret-path-xyz").exchange();

    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("No resource exists at this path.");
  }

  @Test
  void logsResourceConflictAsWarningWithoutSql(CapturedOutput output) {
    useCase.answer =
        id -> {
          throw new ResourceConflictException(
              ResourceConflictException.Kind.DEADLOCK,
              new DataIntegrityViolationException(SECRET_SQL));
        };

    assertThat(mvc.get().uri("/appointments/7")).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(output).contains("WARN").contains("DEADLOCK").doesNotContain(SECRET_SQL);
    assertThat(output).doesNotContain("ERROR");
  }

  @Test
  void doesNotLogClientDisconnectAsError(CapturedOutput output) {
    useCase.answer =
        id -> {
          throw sneakyThrow(new AsyncRequestNotUsableException("Broken pipe"));
        };

    mvc.get().uri("/appointments/7").exchange();

    assertThat(output).doesNotContain("Unexpected error");
  }

  private void assertInvalidId(MvcTestResult result) {
    assertThat(result)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("INVALID_REQUEST");
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(INVALID_ID_DETAIL);
    assertThat(result).bodyJson().doesNotHavePath("$.properties");
    assertThat(useCase.requestedIds).isEmpty();
  }

  private void assertUnexpectedError(MvcTestResult result) {
    assertThat(result)
        .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(UNEXPECTED_DETAIL);
    assertThat(result).bodyJson().doesNotHavePath("$.reason");
    assertThat(result).bodyJson().doesNotHavePath("$.trace");
    assertThat(result).bodyJson().doesNotHavePath("$.exception");
    assertThat(result)
        .bodyText()
        .doesNotContain(SECRET_CONSTRAINT)
        .doesNotContain("INSERT INTO")
        .doesNotContain("23P01")
        .doesNotContain("5432");
  }

  /** Throws a checked exception from a lambda that cannot declare it, as a controller would. */
  @SuppressWarnings("unchecked")
  private static <E extends Throwable> RuntimeException sneakyThrow(Throwable error) throws E {
    throw (E) error;
  }

  private static Appointment appointment(long id) {
    return new Appointment(
        id,
        1,
        2,
        3,
        4,
        5,
        Instant.parse("2026-11-03T02:00:00Z"),
        Instant.parse("2026-11-03T03:30:00Z"),
        AppointmentStatus.CONFIRMED);
  }

  static final class FakeGetAppointment implements GetAppointmentUseCase {

    private final List<Long> requestedIds = new ArrayList<>();
    private LongFunction<Appointment> answer;

    FakeGetAppointment() {
      reset();
    }

    void reset() {
      requestedIds.clear();
      answer =
          id -> {
            throw new AppointmentNotFoundException(id);
          };
    }

    @Override
    public Appointment get(long id) {
      requestedIds.add(id);
      return answer.apply(id);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeConfig {

    @Bean
    FakeGetAppointment fakeGetAppointment() {
      return new FakeGetAppointment();
    }

    @Bean
    FakeBookAppointment fakeBookAppointment() {
      return new FakeBookAppointment();
    }
  }
}
