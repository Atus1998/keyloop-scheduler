package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.domain.Reason;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OpenAPI spec and Swagger UI. Expected status codes and reasons are written out from {@code
 * docs/system-design.md}, section 2, so the test does not reuse the mapping it checks.
 */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class OpenApiDocsApiTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String POST_PATH = "/appointments";
  private static final String GET_PATH = "/appointments/{id}";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final Set<String> REASON_NAMES =
      Arrays.stream(Reason.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
  private static final Pattern WORD = Pattern.compile("[A-Z_]+");

  @Autowired private MockMvcTester mvc;

  @Test
  void servesOpenApi3SpecWithBothAppointmentPaths() {
    var result = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    JsonNode spec = spec();
    assertThat(spec.path("openapi").asString()).startsWith("3.");
    assertThat(operation(spec, POST_PATH, "post").isObject()).isTrue();
    assertThat(operation(spec, GET_PATH, "get").isObject()).isTrue();
  }

  @Test
  void postListsExactlyItsStatusCodes() {
    assertThat(responseCodes(operation(spec(), POST_PATH, "post")))
        .containsExactlyInAnyOrder("201", "200", "400", "404", "409", "415", "422", "500");
  }

  @Test
  void getListsExactlyItsStatusCodes() {
    assertThat(responseCodes(operation(spec(), GET_PATH, "get")))
        .containsExactlyInAnyOrder("200", "400", "404", "500");
  }

  @Test
  void problemSchemaHasReasonEnumFromDomain() {
    JsonNode reason = spec().at("/components/schemas/ProblemDetail/properties/reason");

    List<String> values = new ArrayList<>();
    reason.path("enum").forEach(value -> values.add(value.asString()));
    assertThat(values)
        .containsExactlyElementsOf(Arrays.stream(Reason.values()).map(Enum::name).toList());
  }

  @Test
  void errorResponsesUseProblemSchema() {
    JsonNode spec = spec();
    for (JsonNode operation :
        List.of(operation(spec, POST_PATH, "post"), operation(spec, GET_PATH, "get"))) {
      operation
          .path("responses")
          .properties()
          .forEach(
              entry -> {
                if (entry.getKey().startsWith("4") || entry.getKey().startsWith("5")) {
                  assertThat(
                          entry
                              .getValue()
                              .at("/content/" + escape(PROBLEM_JSON) + "/schema/$ref")
                              .asString())
                      .as("status %s", entry.getKey())
                      .endsWith("/ProblemDetail");
                }
              });
    }
  }

  static Stream<Arguments> reasonsByStatus() {
    return Stream.of(
        Arguments.of(POST_PATH, "post", "400", Set.of("INVALID_REQUEST")),
        Arguments.of(
            POST_PATH,
            "post",
            "404",
            Set.of("DEALERSHIP_NOT_FOUND", "VEHICLE_NOT_FOUND", "SERVICE_TYPE_NOT_FOUND")),
        Arguments.of(
            POST_PATH,
            "post",
            "409",
            Set.of(
                "NO_TECHNICIAN",
                "NO_BAY",
                "BUSY",
                "VEHICLE_BUSY",
                "TECHNICIAN_TAKEN",
                "BAY_TAKEN",
                "SLOT_TAKEN")),
        Arguments.of(POST_PATH, "post", "415", Set.of()),
        Arguments.of(
            POST_PATH,
            "post",
            "422",
            Set.of(
                "START_NOT_WHOLE_MINUTE",
                "START_IN_PAST",
                "START_TOO_FAR_AHEAD",
                "OUTSIDE_OPENING_HOURS",
                "SERVICE_NOT_OFFERED")),
        Arguments.of(POST_PATH, "post", "500", Set.of()),
        Arguments.of(GET_PATH, "get", "400", Set.of("INVALID_REQUEST")),
        Arguments.of(GET_PATH, "get", "404", Set.of("APPOINTMENT_NOT_FOUND")),
        Arguments.of(GET_PATH, "get", "500", Set.of()));
  }

  @ParameterizedTest(name = "{1} {0} {2}")
  @MethodSource("reasonsByStatus")
  void errorDescriptionsListPossibleReasons(
      String path, String method, String status, Set<String> reasons) {
    String description =
        operation(spec(), path, method)
            .path("responses")
            .path(status)
            .path("description")
            .asString();

    assertThat(description).isNotBlank();
    assertThat(reasonsIn(description)).isEqualTo(reasons);
  }

  @ParameterizedTest(name = "{1} {0} {2}")
  @MethodSource("reasonsByStatus")
  void errorResponsesListReasonsAsData(
      String path, String method, String status, Set<String> reasons) {
    JsonNode listed =
        operation(spec(), path, method).path("responses").path(status).path("x-reasons");

    assertThat(listed.isArray()).as("x-reasons is an array").isTrue();
    List<String> names = new ArrayList<>();
    listed.forEach(name -> names.add(name.asString()));
    assertThat(names).containsExactlyInAnyOrderElementsOf(reasons);
  }

  @Test
  void specIsOpenApi31() {
    assertThat(spec().path("openapi").asString()).isEqualTo("3.1.0");
  }

  /** A link with ?url= or ?configUrl= must not load another spec into the UI on this host. */
  @Test
  void swaggerUiTakesNoConfigFromQueryAndHasNoDefaultSpec() {
    var config = mvc.get().uri("/v3/api-docs/swagger-config").exchange();
    var initializer = mvc.get().uri("/swagger-ui/swagger-initializer.js").exchange();

    assertThat(config).bodyJson().extractingPath("$.validatorUrl").isEqualTo("");
    assertThat(initializer).hasStatusOk();
    assertThat(initializer)
        .bodyText()
        .doesNotContain("petstore")
        // springdoc writes the flag only when on, as queryConfigEnabled: "true".
        .doesNotContainPattern("queryConfigEnabled\"?\\s*:\\s*\"?true");
  }

  @Test
  void created201DocumentsLocationHeader() {
    JsonNode created = operation(spec(), POST_PATH, "post").path("responses").path("201");

    assertThat(created.at("/headers/Location").isObject()).isTrue();
    assertThat(created.at("/content/application~1json/schema/$ref").asString())
        .endsWith("/AppointmentResponse");
  }

  @Test
  void ok200IsDescribedAsRetry() {
    JsonNode ok = operation(spec(), POST_PATH, "post").path("responses").path("200");

    assertThat(ok.path("description").asString()).containsIgnoringCase("retry");
    assertThat(ok.at("/headers/Location").isMissingNode()).isTrue();
    assertThat(ok.at("/content/application~1json/schema/$ref").asString())
        .endsWith("/AppointmentResponse");
  }

  @Test
  void requestBodyHasFourRequiredFieldsWithExamples() {
    JsonNode spec = spec();
    JsonNode body = operation(spec, POST_PATH, "post").path("requestBody");
    JsonNode schema = spec.at("/components/schemas/BookAppointmentRequest");

    assertThat(body.path("required").asBoolean()).isTrue();
    assertThat(body.at("/content/application~1json/schema/$ref").asString())
        .endsWith("/BookAppointmentRequest");
    List<String> required = new ArrayList<>();
    schema.path("required").forEach(name -> required.add(name.asString()));
    assertThat(required)
        .containsExactlyInAnyOrder("dealershipId", "vehicleId", "serviceTypeId", "startTime");
    schema
        .path("properties")
        .properties()
        .forEach(
            entry ->
                assertThat(entry.getValue().has("example"))
                    .as("example for %s", entry.getKey())
                    .isTrue());
  }

  @Test
  void swaggerUiHtmlRedirectsToIndex() {
    var result = mvc.get().uri("/swagger-ui.html").exchange();

    assertThat(result.getResponse().getStatus()).isBetween(300, 399);
    assertThat(result.getResponse().getHeader("Location")).contains("/swagger-ui/index.html");
  }

  @Test
  void swaggerUiIndexLoads() {
    var result = mvc.get().uri("/swagger-ui/index.html").exchange();

    assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    assertThat(result).bodyText().contains("swagger-ui");
  }

  @Test
  void swaggerConfigPointsToSpec() {
    var result = mvc.get().uri("/v3/api-docs/swagger-config").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.url").isEqualTo("/v3/api-docs");
  }

  @Test
  void specHasNoActuatorPaths() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();

    List<String> paths = new ArrayList<>();
    spec().path("paths").properties().forEach(entry -> paths.add(entry.getKey()));
    assertThat(paths).isNotEmpty().noneMatch(path -> path.startsWith("/actuator"));
  }

  private JsonNode spec() {
    var result = mvc.get().uri("/v3/api-docs").exchange();
    assertThat(result).hasStatusOk();
    return JSON.readTree(result.getResponse().getContentAsByteArray());
  }

  private static JsonNode operation(JsonNode spec, String path, String method) {
    return spec.path("paths").path(path).path(method);
  }

  private static List<String> responseCodes(JsonNode operation) {
    List<String> codes = new ArrayList<>();
    operation.path("responses").properties().forEach(entry -> codes.add(entry.getKey()));
    return codes;
  }

  /** Reason names in {@code text}, matched as whole words so BUSY is not found in VEHICLE_BUSY. */
  private static Set<String> reasonsIn(String text) {
    Set<String> found = new HashSet<>();
    Matcher words = WORD.matcher(text);
    while (words.find()) {
      if (REASON_NAMES.contains(words.group())) {
        found.add(words.group());
      }
    }
    return Set.copyOf(found);
  }

  /** JSON Pointer escaping for a key that holds {@code /}. */
  private static String escape(String key) {
    return key.replace("~", "~0").replace("/", "~1");
  }
}
