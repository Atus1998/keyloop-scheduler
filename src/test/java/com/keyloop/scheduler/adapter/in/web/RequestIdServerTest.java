package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.PostgresServerTest;
import com.keyloop.scheduler.testsupport.ParallelRunner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/** Request ids under truly parallel HTTP requests to a real server. */
@PostgresServerTest
class RequestIdServerTest {

  private static final int PARALLEL_REQUESTS = 10;
  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final String HEADER = "X-Request-Id";

  private final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build();

  @Value("${local.server.port}")
  private int port;

  @Test
  void givesEachParallelRequestItsOwnId() throws Exception {
    List<Callable<String>> calls = new ArrayList<>();
    for (int i = 0; i < PARALLEL_REQUESTS; i++) {
      calls.add(() -> requestIdOf(get().build()));
    }

    List<String> ids = ParallelRunner.runTogether(calls, TIMEOUT);

    assertThat(ids).doesNotContainNull().doesNotHaveDuplicates();
    ids.forEach(UUID::fromString);
  }

  @Test
  void echoesEachCallersOwnIdUnderParallelLoad() throws Exception {
    List<Callable<String>> calls = new ArrayList<>();
    List<String> sent = new ArrayList<>();
    for (int i = 0; i < PARALLEL_REQUESTS; i++) {
      String id = "client-" + i;
      sent.add(id);
      calls.add(() -> requestIdOf(get().header(HEADER, id).build()));
    }

    List<String> ids = ParallelRunner.runTogether(calls, TIMEOUT);

    assertThat(ids).containsExactlyElementsOf(sent);
  }

  private HttpRequest.Builder get() {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/appointments/1"))
        .timeout(TIMEOUT)
        .GET();
  }

  private String requestIdOf(HttpRequest request) throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.discarding())
        .headers()
        .firstValue(HEADER)
        .orElse(null);
  }
}
