package com.keyloop.scheduler.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DealershipTest {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");

  @ParameterizedTest
  @ValueSource(strings = {"09:00", "08:59"})
  void rejectsClosingAtOrBeforeOpening(String closesAt) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Dealership(1, "London", LONDON, LocalTime.of(9, 0), LocalTime.parse(closesAt)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"09:01", "18:00"})
  void acceptsClosingAfterOpening(String closesAt) {
    assertThatNoException()
        .isThrownBy(
            () ->
                new Dealership(1, "London", LONDON, LocalTime.of(9, 0), LocalTime.parse(closesAt)));
  }
}
