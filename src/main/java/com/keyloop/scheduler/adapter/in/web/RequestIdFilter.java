package com.keyloop.scheduler.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gives every request an id ({@code docs/system-design.md}, section 9). */
@Component
class RequestIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Request-Id";
  static final String MDC_KEY = "requestId";
  static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(request, response);
  }
}
