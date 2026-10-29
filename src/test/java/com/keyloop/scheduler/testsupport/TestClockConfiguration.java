package com.keyloop.scheduler.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A fixed clock for integration tests. {@link #NOW} is years after the real date, so a booking that
 * passes the 90-day rule proves the application reads this clock.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

  public static final Instant NOW = Instant.parse("2030-10-21T00:00:00Z");

  /** A different name from the production bean: Boot does not allow bean overriding. */
  @Bean
  @Primary
  Clock fixedTestClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}
