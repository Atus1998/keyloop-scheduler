package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.application.service.BookingRejectedException;
import com.keyloop.scheduler.domain.Reason;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code X-Request-Id} header and the JSON log lines it ends up in ({@code
 * docs/system-design.md}, section 9). No database: the use cases are hand-written fakes.
 */
@WebMvcTest(AppointmentController.class)
@Import(ApiExceptionHandlerTest.FakeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class RequestIdApiTest {

  private static final String HEADER = "X-Request-Id";
  private static final String VALID_BODY =
      """
      {"dealershipId": 1, "vehicleId": 2, "serviceTypeId": 3,
       "startTime": "2030-10-22T08:00:00Z"}
      """;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvcTester mvc;
  @Autowired private ApiExceptionHandlerTest.FakeGetAppointment getAppointment;
  @Autowired private FakeBookAppointment bookAppointment;

  @BeforeEach
  void resetFakes() {
    getAppointment.reset();
    bookAppointment.reset();
  }

  @Test
  void generatesUuidWhenHeaderIsMissing() {
    var result = mvc.get().uri("/appointments/7").exchange();

    assertThat(result).containsHeader(HEADER);
    UUID.fromString(requestId(result));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "abc",
        "A.b_c-1",
        "0123456789012345678901234567890123456789012345678901234567890123"
      })
  void echoesValidRequestId(String id) {
    var result = mvc.get().uri("/appointments/7").header(HEADER, id).exchange();

    assertThat(requestId(result)).isEqualTo(id);
  }

  @Test
  void usesFirstHeaderWhenSentTwice() {
    var result = mvc.get().uri("/appointments/7").header(HEADER, "first", "second").exchange();

    assertThat(requestId(result)).isEqualTo("first");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "01234567890123456789012345678901234567890123456789012345678901234",
        "a\nb",
        "a\"b",
        "é",
        "a b",
        "a;b"
      })
  void replacesInvalidRequestIdWithNewUuid(String id) {
    var result = mvc.get().uri("/appointments/7").header(HEADER, id).exchange();

    assertThat(requestId(result)).isNotEqualTo(id);
    UUID.fromString(requestId(result));
  }

  @Test
  void doesNotLogInvalidRequestId(CapturedOutput output) {
    failBookingWithUnexpectedError();

    mvc.perform(postValidBody().header(HEADER, "forged\"marker-7f3a"));

    assertThat(output).contains("Unexpected error").doesNotContain("marker-7f3a");
  }

  static Stream<Arguments> errorRequests() {
    return Stream.of(
        Arguments.of("400 bad id", MockMvcRequestBuilders.get("/appointments/abc"), 400),
        Arguments.of("404 unknown id", MockMvcRequestBuilders.get("/appointments/7"), 404),
        Arguments.of("404 unknown path", MockMvcRequestBuilders.get("/no-such-path"), 404),
        Arguments.of("405", MockMvcRequestBuilders.delete("/appointments/7"), 405),
        Arguments.of(
            "415",
            MockMvcRequestBuilders.post("/appointments")
                .contentType(MediaType.TEXT_PLAIN)
                .content("x"),
            415),
        Arguments.of("409", postValidBody(), 409),
        Arguments.of("422", postValidBody(), 422),
        Arguments.of("500", postValidBody(), 500));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("errorRequests")
  void addsRequestIdToErrorResponses(
      String name, MockHttpServletRequestBuilder request, int status) {
    bookAppointment.answer =
        command -> {
          throw switch (status) {
            case 409 -> new BookingRejectedException(Reason.TECHNICIAN_TAKEN);
            case 422 -> new BookingRejectedException(Reason.START_IN_PAST);
            default -> new IllegalStateException("boom");
          };
        };

    var result = mvc.perform(request.header(HEADER, "err-" + status));

    assertThat(result).hasStatus(status);
    assertThat(requestId(result)).isEqualTo("err-" + status);
  }

  @Test
  void logLinesInRequestAreJsonWithRequestId(CapturedOutput output) {
    failBookingWithUnexpectedError();

    mvc.perform(postValidBody().header(HEADER, "json-log-1"));

    JsonNode line = logLine(output, "Unexpected error");
    assertThat(line.path("requestId").asString()).isEqualTo("json-log-1");
    assertThat(level(line)).isEqualTo("ERROR");
  }

  @Test
  void logsHoldNoRequestBodyOrHeaders(CapturedOutput output) {
    failBookingWithUnexpectedError();

    mvc.post()
        .uri("/appointments")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer header-marker-91c2")
        .content(VALID_BODY.replace("}", ", \"note\": \"body-marker-5d1e\"}"))
        .exchange();

    assertThat(output)
        .contains("Unexpected error")
        .doesNotContain("header-marker-91c2")
        .doesNotContain("body-marker-5d1e");
  }

  private void failBookingWithUnexpectedError() {
    bookAppointment.answer =
        command -> {
          throw new IllegalStateException("boom");
        };
  }

  private static MockHttpServletRequestBuilder postValidBody() {
    return MockMvcRequestBuilders.post("/appointments")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_BODY);
  }

  private static String requestId(MvcTestResult result) {
    return result.getResponse().getHeader(HEADER);
  }

  /** The JSON log line whose message is {@code message}; fails if no line is JSON. */
  private static JsonNode logLine(CapturedOutput output, String message) {
    List<JsonNode> lines =
        output
            .getOut()
            .lines()
            .filter(line -> line.startsWith("{"))
            .map(JSON::readTree)
            .filter(node -> message.equals(node.path("message").asString()))
            .toList();
    assertThat(lines).as("JSON log lines with message '%s'", message).isNotEmpty();
    return lines.get(lines.size() - 1);
  }

  /** ECS writes the level as {@code log.level}, flat or nested. */
  private static String level(JsonNode line) {
    JsonNode flat = line.path("log.level");
    return flat.isMissingNode() ? line.path("log").path("level").asString() : flat.asString();
  }
}
