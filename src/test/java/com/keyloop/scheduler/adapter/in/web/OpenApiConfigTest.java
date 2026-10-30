package com.keyloop.scheduler.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keyloop.scheduler.domain.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The error table of the spec, without Spring. */
class OpenApiConfigTest {

  @Test
  void everyReasonStatusHasAMeaning() {
    for (Reason reason : Reason.values()) {
      assertThat(OpenApiConfig.meaning(ReasonProblems.status(reason))).isNotBlank();
    }
  }

  @Test
  void rejectsStatusWithoutMeaning() {
    assertThatThrownBy(() -> OpenApiConfig.meaning(HttpStatus.I_AM_A_TEAPOT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("418");
  }
}
