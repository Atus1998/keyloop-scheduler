package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Health and the latency of {@code POST /appointments} ({@code docs/system-design.md}, section 9).
 * The context is shared with other tests, so metric counts are compared before and after.
 */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class ActuatorApiTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;

  private long dealershipId;
  private long serviceTypeId;
  private long vehicleId;

  @BeforeEach
  void setUp() {
    var fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    long technicianId = fixtures.insertTechnician(dealershipId, "Only");
    fixtures.insertSkill(technicianId, serviceTypeId);
    fixtures.insertBay(dealershipId);
    vehicleId = fixtures.insertVehicle(fixtures.insertCustomer(), "AB12 CDE");
  }

  @Test
  void healthIsUpWithDatabaseComponentUp() {
    var result = mvc.get().uri("/actuator/health").exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
    assertThat(result).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
  }

  @Test
  void healthHidesDatabaseDetails() {
    var result = mvc.get().uri("/actuator/health").exchange();

    assertThat(result).bodyJson().doesNotHavePath("$.components.db.details");
    assertThat(result).bodyText().doesNotContain("PostgreSQL").doesNotContain("jdbc");
  }

  @Test
  void healthResponseCarriesRequestId() {
    var result = mvc.get().uri("/actuator/health").exchange();

    UUID.fromString(result.getResponse().getHeader("X-Request-Id"));
  }

  @Test
  void recordsPostLatencyFor201() {
    String body =
        """
        {"dealershipId": %d, "vehicleId": %d, "serviceTypeId": %d,
         "startTime": "2030-10-22T08:00:00Z"}
        """
            .formatted(dealershipId, vehicleId, serviceTypeId);

    assertPostRecorded(body, HttpStatus.CREATED);
  }

  @Test
  void recordsPostLatencyForRejectedBooking() {
    String unknownDealership =
        """
        {"dealershipId": %d, "vehicleId": %d, "serviceTypeId": %d,
         "startTime": "2030-10-22T08:00:00Z"}
        """
            .formatted(dealershipId + 1000, vehicleId, serviceTypeId);

    assertPostRecorded(unknownDealership, HttpStatus.NOT_FOUND);
    assertPostRecorded("{\"dealershipId\": 1,", HttpStatus.BAD_REQUEST);
  }

  private void assertPostRecorded(String body, HttpStatus status) {
    double before = postCount(status);

    assertThat(
            mvc.post().uri("/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
        .hasStatus(status);

    assertThat(postCount(status)).isEqualTo(before + 1);
    assertThat(postStatistic(status, "TOTAL_TIME")).isPositive();
  }

  private double postCount(HttpStatus status) {
    return postStatistic(status, "COUNT");
  }

  /** 0 when no POST with this status has been recorded yet (the endpoint then gives 404). */
  private double postStatistic(HttpStatus status, String statistic) {
    var result =
        mvc.get()
            .uri("/actuator/metrics/http.server.requests")
            .param("tag", "method:POST", "uri:/appointments", "status:" + status.value())
            .exchange();
    if (result.getResponse().getStatus() == HttpStatus.NOT_FOUND.value()) {
      return 0;
    }
    assertThat(result).hasStatusOk();
    JsonNode body = JSON.readTree(result.getResponse().getContentAsByteArray());
    for (JsonNode measurement : body.path("measurements")) {
      if (statistic.equals(measurement.path("statistic").asString())) {
        return measurement.path("value").asDouble();
      }
    }
    throw new AssertionError("No " + statistic + " in " + body);
  }
}
