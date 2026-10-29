package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.PostgresServerTest;
import com.keyloop.scheduler.testsupport.ParallelRunner;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proof that parallel requests never double book ({@code docs/system-design.md}, section 6). Real
 * HTTP to a real server, so the requests truly overlap. The dealership has one technician and one
 * bay, so only one booking per slot can win. Each test runs 5 times.
 */
@PostgresServerTest
class BookAppointmentConcurrencyApiTest {

  private static final int PARALLEL_REQUESTS = 10;
  private static final int REPEATS = 5;
  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  /** 09:00 London time (BST) on Tuesday 22 October 2030. */
  private static final Instant START = Instant.parse("2030-10-22T08:00:00Z");

  /** Every 409 a lost race for the one technician and the one bay may give. */
  private static final Set<String> SLOT_LOST_REASONS =
      Set.of("TECHNICIAN_TAKEN", "BAY_TAKEN", "SLOT_TAKEN", "NO_TECHNICIAN", "NO_BAY", "BUSY");

  private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
  private static final Pattern REASON = Pattern.compile("\"reason\"\\s*:\\s*\"(\\w+)\"");

  private final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build();

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private long dealershipId;
  private long serviceTypeId;
  private List<Long> vehicleIds;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    long technicianId = fixtures.insertTechnician(dealershipId, "Only");
    fixtures.insertSkill(technicianId, serviceTypeId);
    fixtures.insertBay(dealershipId);
    long customerId = fixtures.insertCustomer();
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < PARALLEL_REQUESTS; i++) {
      ids.add(fixtures.insertVehicle(customerId, "RACE " + i));
    }
    vehicleIds = List.copyOf(ids);
  }

  @RepeatedTest(REPEATS)
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void oneOfManyVehiclesWinsTheSlotAndTheRestGet409() throws Exception {
    List<Reply> replies = sendTogether(vehicleIds.stream().map(id -> body(id, START)).toList());

    assertThat(replies).as(describe(replies)).filteredOn(r -> r.status() == 201).hasSize(1);
    assertThat(replies)
        .as(describe(replies))
        .filteredOn(r -> r.status() != 201)
        .allSatisfy(
            r -> {
              assertThat(r.status()).isEqualTo(409);
              assertThat(SLOT_LOST_REASONS).contains(r.reason());
            });
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @RepeatedTest(REPEATS)
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void identicalRequestsGiveOne201AndTheSameAppointmentToTheRest() throws Exception {
    String body = body(vehicleIds.get(0), START);
    List<Reply> replies = sendTogether(Collections.nCopies(PARALLEL_REQUESTS, body));

    List<Reply> created = replies.stream().filter(r -> r.status() == 201).toList();
    assertThat(created).as(describe(replies)).hasSize(1);
    Long id = created.get(0).id();
    assertThat(id).isNotNull();
    // A rare double deadlock while the winner has not committed gives 409 SLOT_TAKEN (accepted).
    assertThat(replies)
        .as(describe(replies))
        .filteredOn(r -> r.status() != 201)
        .allSatisfy(
            r -> {
              if (r.status() == 200) {
                assertThat(r.id()).isEqualTo(id);
                assertThat(r.hasLocation()).isFalse();
              } else {
                assertThat(r.status()).isEqualTo(409);
                assertThat(r.reason()).isEqualTo("SLOT_TAKEN");
              }
            });
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @RepeatedTest(REPEATS)
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void oneVehicleWithOverlappingStartsGetsOne201AndVehicleBusyOrSlotTakenForTheRest()
      throws Exception {
    long vehicleId = vehicleIds.get(0);
    List<String> bodies = new ArrayList<>();
    for (int minute = 0; minute < PARALLEL_REQUESTS; minute++) {
      bodies.add(body(vehicleId, START.plusSeconds(60L * minute)));
    }

    List<Reply> replies = sendTogether(bodies);

    assertThat(replies).as(describe(replies)).filteredOn(r -> r.status() == 201).hasSize(1);
    // All three constraints clash, so losers often deadlock twice before the winner commits and
    // get 409 SLOT_TAKEN (accepted: look-again table, row 5).
    assertThat(replies)
        .as(describe(replies))
        .filteredOn(r -> r.status() != 201)
        .allSatisfy(
            r -> {
              assertThat(r.status()).isEqualTo(409);
              assertThat(r.reason()).isIn("VEHICLE_BUSY", "SLOT_TAKEN");
            });
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  private List<Reply> sendTogether(List<String> bodies) throws Exception {
    List<Callable<Reply>> tasks = bodies.stream().<Callable<Reply>>map(b -> () -> send(b)).toList();
    return ParallelRunner.runTogether(tasks, TIMEOUT);
  }

  private Reply send(String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/appointments"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    return new Reply(
        response.statusCode(),
        find(ID, response.body()).map(Long::valueOf).orElse(null),
        find(REASON, response.body()).orElse(null),
        response.headers().firstValue("Location").isPresent());
  }

  private String body(long vehicleId, Instant start) {
    return """
        {"dealershipId": %d, "vehicleId": %d, "serviceTypeId": %d, "startTime": "%s"}
        """
        .formatted(dealershipId, vehicleId, serviceTypeId, start);
  }

  private static Optional<String> find(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static String describe(List<Reply> replies) {
    return "replies: " + replies;
  }

  /** {@code id} and {@code reason} are null when the body has none. */
  private record Reply(int status, Long id, String reason, boolean hasLocation) {}
}
