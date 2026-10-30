package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.testsupport.TestClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Health when the database cannot be reached. Its own context and container, so stopping the
 * database does not touch other tests. Short pool timeouts keep the failing check fast.
 */
@SpringBootTest(
    properties = {
      "spring.flyway.locations=classpath:db/migration",
      "spring.datasource.hikari.connection-timeout=250",
      "spring.datasource.hikari.validation-timeout=250"
    })
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
@AutoConfigureMockMvc
@DirtiesContext
class HealthDatabaseDownTest {

  @Autowired private MockMvcTester mvc;
  @Autowired private PostgreSQLContainer postgres;

  @Test
  void healthIs503WithDatabaseDownWhenDatabaseIsUnreachable() {
    postgres.stop();

    var result = mvc.get().uri("/actuator/health").exchange();

    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DOWN");
    assertThat(result).bodyJson().extractingPath("$.components.db.status").isEqualTo("DOWN");
    assertThat(result).bodyJson().doesNotHavePath("$.components.db.details");
    assertThat(result).bodyText().doesNotContain("jdbc").doesNotContain("Connection");
  }
}
