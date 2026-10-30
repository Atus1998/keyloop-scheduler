package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keyloop.scheduler.testsupport.ParallelRunner;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The request id filter on its own, without Spring: MDC life cycle, ordering, error dispatch. */
class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void runsBeforeOtherFilters() {
    assertThat(OrderUtils.getOrder(RequestIdFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }

  @Test
  void putsRequestIdInMdcDuringChainAndRemovesItAfter() throws Exception {
    var request = new MockHttpServletRequest("GET", "/appointments/7");
    request.addHeader(RequestIdFilter.HEADER, "abc-123");
    var seen = new AtomicReference<String>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));

    assertThat(seen.get()).isEqualTo("abc-123");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void removesRequestIdFromMdcWhenChainThrows() {
    var request = new MockHttpServletRequest("GET", "/appointments/7");

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (req, res) -> {
                      throw new ServletException("boom");
                    }))
        .isInstanceOf(ServletException.class);
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void setsHeaderBeforeChainRuns() {
    var response = new MockHttpServletResponse();
    var headerDuringChain = new AtomicReference<String>();

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest("GET", "/appointments/7"),
                    response,
                    (req, res) -> {
                      headerDuringChain.set(response.getHeader(RequestIdFilter.HEADER));
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(headerDuringChain.get()).isNotNull();
    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(headerDuringChain.get());
  }

  @Test
  void doesNotLeakIdToNextRequestOnSameThread() throws Exception {
    var first = new MockHttpServletRequest("GET", "/appointments/7");
    first.addHeader(RequestIdFilter.HEADER, "first-id");
    filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

    var second = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest("GET", "/appointments/7"), second, new MockFilterChain());

    assertThat(second.getHeader(RequestIdFilter.HEADER)).isNotEqualTo("first-id");
    UUID.fromString(second.getHeader(RequestIdFilter.HEADER));
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  /** All requests are inside the chain at once when each reads its MDC. */
  @Test
  void keepsEachParallelRequestsOwnIdInMdc() throws Exception {
    int parallel = 10;
    var allInside = new CyclicBarrier(parallel);
    List<String> sent = new ArrayList<>();
    List<Callable<String>> calls = new ArrayList<>();
    for (int i = 0; i < parallel; i++) {
      String id = "thread-" + i;
      sent.add(id);
      calls.add(() -> mdcSeenInChain(id, allInside));
    }

    List<String> seen = ParallelRunner.runTogether(calls, Duration.ofSeconds(10));

    assertThat(seen).containsExactlyElementsOf(sent);
  }

  private String mdcSeenInChain(String id, CyclicBarrier allInside) throws Exception {
    var request = new MockHttpServletRequest("GET", "/appointments/7");
    request.addHeader(RequestIdFilter.HEADER, id);
    var seen = new AtomicReference<String>();
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          try {
            allInside.await(5, TimeUnit.SECONDS);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
          seen.set(MDC.get(RequestIdFilter.MDC_KEY));
        });
    return seen.get();
  }

  /** Tomcat forwards an unhandled error to the error page as a second dispatch. */
  @Test
  void reusesSameIdOnErrorDispatch() throws Exception {
    var request = new MockHttpServletRequest("GET", "/error");
    request.setDispatcherType(DispatcherType.ERROR);
    request.setAttribute(RequestIdFilter.ATTRIBUTE, "kept-id");
    request.addHeader(RequestIdFilter.HEADER, "other-id");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("kept-id");
  }
}
