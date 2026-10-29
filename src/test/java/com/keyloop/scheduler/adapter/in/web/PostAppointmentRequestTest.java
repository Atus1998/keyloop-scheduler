package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.service.BookingRejectedException;
import com.keyloop.scheduler.domain.Reason;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Request shape and error mapping of POST /appointments. No database: the use case is a fake. */
@WebMvcTest(AppointmentController.class)
@Import(PostAppointmentRequestTest.FakeConfig.class)
class PostAppointmentRequestTest {

  private static final String VALID =
      """
      {"dealershipId": 1, "vehicleId": 42, "serviceTypeId": 3,
       "startTime": "2026-11-03T09:00:00+07:00"}
      """;

  @Autowired private MockMvcTester mvc;
  @Autowired private FakeBookAppointment useCase;

  @BeforeEach
  void resetFake() {
    useCase.reset();
  }

  @Test
  void passesParsedCommandAndReturns201WithLocation() {
    MvcTestResult result = post(VALID);

    assertThat(result)
        .hasStatus(HttpStatus.CREATED)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/appointments/101");
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": 101,
              "status": "CONFIRMED",
              "dealershipId": 1,
              "vehicleId": 42,
              "serviceTypeId": 3,
              "technicianId": 7,
              "bayId": 2,
              "startTime": "2026-11-03T02:00:00Z",
              "endTime": "2026-11-03T03:30:00Z"
            }
            """);
    assertThat(useCase.commands)
        .containsExactly(
            new BookAppointmentCommand(1, 42, 3, Instant.parse("2026-11-03T02:00:00Z")));
  }

  @Test
  void returns200WithoutLocationWhenUseCaseReportsRetry() {
    useCase.retry = true;

    MvcTestResult result = post(VALID);

    assertThat(result)
        .hasStatus(HttpStatus.OK)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .doesNotContainHeader("Location");
    assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(101);
  }

  @Test
  void ignoresUnknownFields() {
    String body =
        """
        {"dealershipId": 1, "vehicleId": 42, "serviceTypeId": 3,
         "startTime": "2026-11-03T09:00:00Z", "unknown": {"nested": [1, 2]}}
        """;

    assertThat(post(body)).hasStatus(HttpStatus.CREATED);
    assertThat(useCase.commands).hasSize(1);
  }

  @Test
  void acceptsUtcAndFractionalSecondsInStartTime() {
    String body =
        """
        {"dealershipId": 1, "vehicleId": 42, "serviceTypeId": 3,
         "startTime": "2026-11-03T02:00:00.5Z"}
        """;

    assertThat(post(body)).hasStatus(HttpStatus.CREATED);
    assertThat(useCase.commands.getFirst().startTime())
        .isEqualTo(Instant.parse("2026-11-03T02:00:00.5Z"));
  }

  @ParameterizedTest
  @MethodSource("invalidBodies")
  void returns400WithoutCallingUseCaseForInvalidBody(String body) {
    MvcTestResult result = post(body);

    assertThat(result)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("INVALID_REQUEST");
    assertThat(result).bodyJson().doesNotHavePath("$.properties");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .isNotBlank()
        .doesNotContainIgnoringCase("jackson")
        .doesNotContain("java.")
        .doesNotContain("Cannot ")
        .doesNotContain("line:");
    assertThat(useCase.commands).isEmpty();
  }

  static Stream<String> invalidBodies() {
    return Stream.of(
        "{",
        "",
        "null",
        "[]",
        "\"text\"",
        body("1", "42", "3", null),
        """
        {"vehicleId": 42, "serviceTypeId": 3, "startTime": "2026-11-03T09:00:00Z"}
        """,
        """
        {"dealershipId": 1, "serviceTypeId": 3, "startTime": "2026-11-03T09:00:00Z"}
        """,
        """
        {"dealershipId": 1, "vehicleId": 42, "startTime": "2026-11-03T09:00:00Z"}
        """,
        """
        {"dealershipId": 1, "vehicleId": 42, "serviceTypeId": 3}
        """,
        body("null", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("1", "null", "3", "\"2026-11-03T09:00:00Z\""),
        body("1", "42", "null", "\"2026-11-03T09:00:00Z\""),
        body("1", "42", "3", "null"),
        body("\"abc\"", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("{}", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("[]", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("true", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("0", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("1", "-1", "3", "\"2026-11-03T09:00:00Z\""),
        body("1", "42", "0", "\"2026-11-03T09:00:00Z\""),
        body("9223372036854775808", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("\"1\"", "42", "3", "\"2026-11-03T09:00:00Z\""),
        body("1", "1.0", "3", "\"2026-11-03T09:00:00Z\""),
        body("1", "42", "1.5", "\"2026-11-03T09:00:00Z\""),
        body("1", "42", "3", "\"2026-11-03T09:00:00\""),
        body("1", "42", "3", "\"2026-11-03\""),
        body("1", "42", "3", "1730000000"),
        body("1", "42", "3", "\"1730000000\""),
        body("1", "42", "3", "\"garbage\""),
        body("1", "42", "3", "\"\""),
        body("1", "42", "3", "{}"),
        body("1", "42", "3", "\"2026-11-03T09:00:00.1234567890+07:00\""),
        body("1", "42", "3", "\"2026-11-03 09:00:00+07:00\""),
        body("1", "42", "3", "\"2026-11-03T09:00:00+07:00[Asia/Ho_Chi_Minh]\""));
  }

  @Test
  void namesInvalidFieldsInDetail() {
    MvcTestResult result =
        post(
            """
            {"dealershipId": 0, "serviceTypeId": 3, "startTime": "2026-11-03T09:00:00Z"}
            """);

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("dealershipId")
        .contains("vehicleId")
        .doesNotContain("serviceTypeId");
  }

  @Test
  void returns415WithoutReasonWhenContentTypeIsMissing() {
    MvcTestResult result = mvc.post().uri("/appointments").content(VALID).exchange();

    assertThat(result)
        .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().doesNotHavePath("$.reason");
    assertThat(useCase.commands).isEmpty();
  }

  @Test
  void unsupportedContentTypeDetailDoesNotEchoHeader() {
    MvcTestResult result =
        mvc.post()
            .uri("/appointments")
            .contentType("text/x-secret-marker")
            .content(VALID)
            .exchange();

    assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("The request body must be sent as application/json.");
    assertThat(result).bodyText().doesNotContain("x-secret-marker");
  }

  @Test
  void rejectsOverlongStartTimeAsInvalidField() {
    String longStart = "2026-11-03T09:00:00" + "0".repeat(60) + "+07:00";

    MvcTestResult result = post(body("1", "42", "3", "\"" + longStart + "\""));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("INVALID_REQUEST");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("These fields are missing or invalid: startTime.");
    assertThat(useCase.commands).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "START_NOT_WHOLE_MINUTE, 422",
    "START_IN_PAST, 422",
    "START_TOO_FAR_AHEAD, 422",
    "OUTSIDE_OPENING_HOURS, 422",
    "SERVICE_NOT_OFFERED, 422",
    "VEHICLE_BUSY, 409",
    "BUSY, 409",
    "NO_TECHNICIAN, 409",
    "NO_BAY, 409"
  })
  void mapsBookingRejectionToStatusWithTopLevelReason(Reason reason, int status) {
    useCase.answer =
        command -> {
          throw new BookingRejectedException(reason);
        };

    MvcTestResult result = post(VALID);

    assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo(reason.name());
    assertThat(result).bodyJson().extractingPath("$.detail").asString().isNotBlank();
    assertThat(result).bodyJson().doesNotHavePath("$.properties");
  }

  @ParameterizedTest
  @CsvSource({
    "DEALERSHIP_NOT_FOUND, Dealership 5 was not found.",
    "VEHICLE_NOT_FOUND, Vehicle 5 was not found.",
    "SERVICE_TYPE_NOT_FOUND, Service type 5 was not found."
  })
  void mapsUnknownReferenceTo404WithIdInDetail(Reason reason, String detail) {
    useCase.answer =
        command -> {
          throw new BookingRejectedException(reason, 5);
        };

    MvcTestResult result = post(VALID);

    assertThat(result)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo(reason.name());
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
  }

  private MvcTestResult post(String body) {
    return mvc.post()
        .uri("/appointments")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  /** A body with raw JSON values; a null argument leaves that field out. */
  private static String body(
      String dealershipId, String vehicleId, String serviceTypeId, String startTime) {
    var fields = new StringBuilder("{");
    appendField(fields, "dealershipId", dealershipId);
    appendField(fields, "vehicleId", vehicleId);
    appendField(fields, "serviceTypeId", serviceTypeId);
    appendField(fields, "startTime", startTime);
    return fields.append('}').toString();
  }

  private static void appendField(StringBuilder fields, String name, String rawValue) {
    if (rawValue == null) {
      return;
    }
    if (fields.length() > 1) {
      fields.append(", ");
    }
    fields.append('"').append(name).append("\": ").append(rawValue);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeConfig {

    @Bean
    FakeBookAppointment fakeBookAppointment() {
      return new FakeBookAppointment();
    }

    @Bean
    ApiExceptionHandlerTest.FakeGetAppointment fakeGetAppointment() {
      return new ApiExceptionHandlerTest.FakeGetAppointment();
    }
  }
}
