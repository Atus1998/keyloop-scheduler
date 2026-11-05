package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proof that parallel requests never double book and that they wait in line for their dealership
 * and day ({@code docs/system-design.md}, section 6). Real HTTP to a real server, so the requests
 * truly overlap. Each test runs 5 times.
 */
@PostgresServerTest
class BookAppointmentConcurrencyApiTest {

  private static final Logger log =
      LoggerFactory.getLogger(BookAppointmentConcurrencyApiTest.class);

  private static final int PARALLEL_REQUESTS = 10;
  private static final int REPEATS = 5;
  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  /** 09:00 London time (BST) on Tuesday 22 October 2030. */
  private static final Instant START = Instant.parse("2030-10-22T08:00:00Z");

  /** The next day, so the warm-up request waits for no one in the test. */
  private static final Instant WARM_UP_START = START.plus(Duration.ofDays(1));

  private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
  private static final Pattern TECHNICIAN_ID = Pattern.compile("\"technicianId\"\\s*:\\s*(\\d+)");
  private static final Pattern BAY_ID = Pattern.compile("\"bayId\"\\s*:\\s*(\\d+)");
  private static final Pattern REASON = Pattern.compile("\"reason\"\\s*:\\s*\"(\\w+)\"");

  private final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build();

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private long dealershipId;
  private long serviceTypeId;
  private long customerId;
  private List<Long> vehicleIds;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    customerId = fixtures.insertCustomer();
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < PARALLEL_REQUESTS; i++) {
      ids.add(fixtures.insertVehicle(customerId, "RACE " + i));
    }
    vehicleIds = List.copyOf(ids);
  }

  /** Technicians, bays, expected 201s, reason of every other reply, round. */
  static Stream<Arguments> capacities() {
    List<Arguments> cases =
        List.of(
            arguments(10, 10, 10, null),
            arguments(1, 1, 1, "BUSY"),
            arguments(3, 10, 3, "NO_TECHNICIAN"),
            arguments(10, 3, 3, "NO_BAY"));
    return IntStream.rangeClosed(1, REPEATS)
        .boxed()
        .flatMap(
            round ->
                cases.stream()
                    .map(c -> arguments(c.get()[0], c.get()[1], c.get()[2], c.get()[3], round)));
  }

  @ParameterizedTest(name = "{0} technicians, {1} bays, round {4}")
  @MethodSource("capacities")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void manyVehiclesGetEveryFreeSlotAndTheRestGet409WithTheRealReason(
      int technicians, int bays, int expectedCreated, String lostReason, int round)
      throws Exception {
    addCapacity(dealershipId, technicians, bays);
    warmUp();

    List<Reply> replies =
        sendTogether(vehicleIds.stream().map(id -> body(dealershipId, id, START)).toList());
    logTimings(technicians + " technicians, " + bays + " bays, round " + round, replies);

    List<Reply> created = replies.stream().filter(r -> r.status() == 201).toList();
    assertThat(created).as(describe(replies)).hasSize(expectedCreated);
    assertThat(created).extracting(Reply::technicianId).doesNotHaveDuplicates();
    assertThat(created).extracting(Reply::bayId).doesNotHaveDuplicates();
    assertThat(replies)
        .as(describe(replies))
        .filteredOn(r -> r.status() != 201)
        .allSatisfy(
            r -> {
              assertThat(r.status()).isEqualTo(409);
              assertThat(r.reason()).isEqualTo(lostReason);
            });
    assertThat(fixtures.countAppointments() - 1).isEqualTo(expectedCreated);
  }

  @RepeatedTest(REPEATS)
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void identicalRequestsGiveOne201AndTheSameAppointmentToTheRest() throws Exception {
    addCapacity(dealershipId, 1, 1);
    String body = body(dealershipId, vehicleIds.get(0), START);
    List<Reply> replies = sendTogether(Collections.nCopies(PARALLEL_REQUESTS, body));

    List<Reply> created = replies.stream().filter(r -> r.status() == 201).toList();
    assertThat(created).as(describe(replies)).hasSize(1);
    Long id = created.get(0).id();
    assertThat(id).isNotNull();
    assertThat(replies)
        .as(describe(replies))
        .filteredOn(r -> r.status() != 201)
        .allSatisfy(
            r -> {
              assertThat(r.status()).isEqualTo(200);
              assertThat(r.id()).isEqualTo(id);
              assertThat(r.hasLocation()).isFalse();
            });
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @RepeatedTest(REPEATS)
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void oneVehicleWithOverlappingStartsGetsOne201AndVehicleBusyForTheRest() throws Exception {
    addCapacity(dealershipId, 1, 1);
    long vehicleId = vehicleIds.get(0);
    List<String> bodies = new ArrayList<>();
    for (int minute = 0; minute < PARALLEL_REQUESTS; minute++) {
      bodies.add(body(dealershipId, vehicleId, START.plusSeconds(60L * minute)));
    }

    List<Reply> replies = sendTogether(bodies);

    assertThat(replies).as(describe(replies)).filteredOn(r -> r.status() == 201).hasSize(1);
    assertThat(replies)
        .as(describe(replies))
        .filteredOn(r -> r.status() != 201)
        .allSatisfy(
            r -> {
              assertThat(r.status()).isEqualTo(409);
              assertThat(r.reason()).isEqualTo("VEHICLE_BUSY");
            });
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  /** Two dealerships wait in different lines, so only the vehicle constraint stops the second. */
  @RepeatedTest(REPEATS)
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void oneVehicleAtTwoDealershipsGetsOne201AndVehicleBusy() throws Exception {
    addCapacity(dealershipId, 1, 1);
    long otherDealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    addCapacity(otherDealershipId, 1, 1);
    long vehicleId = vehicleIds.get(0);

    List<Reply> replies =
        sendTogether(
            List.of(
                body(dealershipId, vehicleId, START), body(otherDealershipId, vehicleId, START)));

    assertThat(replies).as(describe(replies)).filteredOn(r -> r.status() == 201).hasSize(1);
    assertThat(replies)
        .as(describe(replies))
        .filteredOn(r -> r.status() != 201)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.status()).isEqualTo(409);
              assertThat(r.reason()).isEqualTo("VEHICLE_BUSY");
            });
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  private void addCapacity(long dealership, int technicians, int bays) {
    for (int i = 0; i < technicians; i++) {
      long technicianId = fixtures.insertTechnician(dealership, "Technician " + i);
      fixtures.insertSkill(technicianId, serviceTypeId);
    }
    for (int i = 0; i < bays; i++) {
      fixtures.insertBay(dealership);
    }
  }

  /** One booking on another day first, so the timed requests do not pay for a cold start. */
  private void warmUp() throws Exception {
    long vehicleId = fixtures.insertVehicle(customerId, "WARM UP");
    Reply reply = send(body(dealershipId, vehicleId, WARM_UP_START));
    assertThat(reply.status()).as("warm-up reply %s", reply).isEqualTo(201);
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
    long started = System.nanoTime();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    return new Reply(
        response.statusCode(),
        find(ID, response.body()).map(Long::valueOf).orElse(null),
        find(TECHNICIAN_ID, response.body()).map(Long::valueOf).orElse(null),
        find(BAY_ID, response.body()).map(Long::valueOf).orElse(null),
        find(REASON, response.body()).orElse(null),
        response.headers().firstValue("Location").isPresent(),
        millis);
  }

  private String body(long dealership, long vehicleId, Instant start) {
    return """
        {"dealershipId": %d, "vehicleId": %d, "serviceTypeId": %d, "startTime": "%s"}
        """
        .formatted(dealership, vehicleId, serviceTypeId, start);
  }

  /** Numbers only, no assertion: the time of the last request in line depends on the machine. */
  private static void logTimings(String label, List<Reply> replies) {
    List<Long> millis = replies.stream().map(Reply::millis).sorted().toList();
    log.info(
        "Parallel booking timings ({}): max={} ms, all={} ms",
        label,
        millis.get(millis.size() - 1),
        millis);
  }

  private static Optional<String> find(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static String describe(List<Reply> replies) {
    return "replies: " + replies;
  }

  /** The ids and {@code reason} are null when the body has none. */
  private record Reply(
      int status,
      Long id,
      Long technicianId,
      Long bayId,
      String reason,
      boolean hasLocation,
      long millis) {}
}
