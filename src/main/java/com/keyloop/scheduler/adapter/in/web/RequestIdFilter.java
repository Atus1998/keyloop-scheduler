package com.keyloop.scheduler.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id ({@code docs/system-design.md}, section 9): the caller's {@code
 * X-Request-Id} when it is safe to log, a new UUID otherwise. The id goes on the response before
 * anything else runs, so error responses carry it too, and into the MDC for the log lines of this
 * request. First in the chain, so other filters log with it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Request-Id";
  static final String MDC_KEY = "requestId";

  /** Keeps the id for the error dispatch Tomcat makes after an unhandled error. */
  static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

  /** No spaces, quotes or line breaks, so the id cannot forge or break a log line. */
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = requestId(request);
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(HEADER, id);
    MDC.put(MDC_KEY, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  private static String requestId(HttpServletRequest request) {
    if (request.getAttribute(ATTRIBUTE) instanceof String kept) {
      return kept;
    }
    String sent = request.getHeader(HEADER);
    if (sent != null && SAFE_ID.matcher(sent).matches()) {
      return sent;
    }
    return UUID.randomUUID().toString();
  }
}
