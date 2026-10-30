package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The {@code prod} profile turns the API docs and Swagger UI off. */
@PostgresIntegrationTest
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProdProfileApiTest {

  @Autowired private MockMvcTester mvc;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v3/api-docs",
        "/v3/api-docs/swagger-config",
        "/swagger-ui.html",
        "/swagger-ui/index.html",
        "/webjars/swagger-ui/index.html"
      })
  void servesNoApiDocs(String path) {
    assertThat(mvc.get().uri(path)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void healthStillAnswers() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
  }
}
