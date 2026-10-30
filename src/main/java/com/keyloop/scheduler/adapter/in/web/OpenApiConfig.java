package com.keyloop.scheduler.adapter.in.web;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.keyloop.scheduler.domain.Reason;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * The OpenAPI spec ({@code /v3/api-docs}) and Swagger UI. Success responses are annotated on {@link
 * AppointmentController}; every error response is added here from one table, with the reasons each
 * status can carry worked out from {@link ReasonProblems#status}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  static final String PROBLEM_SCHEMA = "ProblemDetail";

  private static final String PROBLEM_JSON = "application/problem+json";

  /** Every reason but the appointment lookup, which only GET can give. */
  private static final Set<Reason> POST_REASONS =
      EnumSet.complementOf(EnumSet.of(Reason.APPOINTMENT_NOT_FOUND));

  private static final Set<Reason> GET_REASONS =
      EnumSet.of(Reason.INVALID_REQUEST, Reason.APPOINTMENT_NOT_FOUND);

  private static final Map<HttpStatus, String> MEANINGS =
      Map.of(
          HttpStatus.BAD_REQUEST, "The request is not valid.",
          HttpStatus.NOT_FOUND, "Something the request names does not exist.",
          HttpStatus.CONFLICT, "The time cannot be booked.",
          HttpStatus.UNPROCESSABLE_CONTENT, "The booking breaks a rule.",
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The body is not sent as application/json.",
          HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

  @Bean
  OpenAPI schedulerOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Keyloop Service Scheduler API")
                .version("v1")
                .description("Books dealership service appointments without double booking."));
  }

  /** springdoc hands over its mutable model, so this adds to it in place. */
  @Bean
  OpenApiCustomizer problemResponses() {
    return openApi -> {
      openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
      addErrors(
          operation(openApi, "/appointments", PathItem::getPost),
          POST_REASONS,
          List.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, HttpStatus.INTERNAL_SERVER_ERROR));
      addErrors(
          operation(openApi, "/appointments/{id}", PathItem::getGet),
          GET_REASONS,
          List.of(HttpStatus.INTERNAL_SERVER_ERROR));
    };
  }

  private static Operation operation(
      OpenAPI openApi, String path, Function<PathItem, Operation> method) {
    PathItem item = openApi.getPaths() == null ? null : openApi.getPaths().get(path);
    Operation operation = item == null ? null : method.apply(item);
    if (operation == null) {
      throw new IllegalStateException("No operation in the OpenAPI spec for " + path);
    }
    return operation;
  }

  private static void addErrors(
      Operation operation, Set<Reason> reasons, List<HttpStatus> statusesWithoutReason) {
    Map<HttpStatus, List<Reason>> byStatus =
        reasons.stream().collect(groupingBy(ReasonProblems::status, TreeMap::new, toList()));
    byStatus.forEach(
        (status, statusReasons) ->
            operation
                .getResponses()
                .addApiResponse(
                    code(status),
                    problem(
                        meaning(status)
                            + " Reasons: "
                            + String.join(", ", names(statusReasons))
                            + ".",
                        names(statusReasons))));
    statusesWithoutReason.forEach(
        status ->
            operation
                .getResponses()
                .addApiResponse(code(status), problem(meaning(status) + " No reason.", List.of())));
  }

  /** What an error status means; fails for a status the table does not know yet. */
  static String meaning(HttpStatus status) {
    String meaning = MEANINGS.get(status);
    if (meaning == null) {
      throw new IllegalStateException(
          "No meaning for status " + status.value() + " in OpenApiConfig");
    }
    return meaning;
  }

  /** {@code x-reasons} lists the reason codes as data, for clients that generate code. */
  private static ApiResponse problem(String description, List<String> reasons) {
    Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA);
    return new ApiResponse()
        .description(description)
        .content(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(ref)))
        .extensions(Map.of("x-reasons", reasons));
  }

  private static Schema<?> problemSchema() {
    List<String> reasonNames = Arrays.stream(Reason.values()).map(Enum::name).toList();
    return new ObjectSchema()
        .description("Problem detail (RFC 9457) with a reason code.")
        .addProperty("type", new StringSchema().example("about:blank"))
        .addProperty("title", new StringSchema().example("Conflict"))
        .addProperty("status", new IntegerSchema().example(409))
        .addProperty("detail", new StringSchema())
        .addProperty("instance", new StringSchema().example("/appointments"))
        .addProperty(
            "reason",
            new StringSchema()
                ._enum(reasonNames)
                .description("Why the request failed. Absent for 415 and 500."));
  }

  private static String code(HttpStatus status) {
    return String.valueOf(status.value());
  }

  private static List<String> names(List<Reason> reasons) {
    return reasons.stream().map(Enum::name).toList();
  }
}
