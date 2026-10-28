package com.keyloop.scheduler.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceTypeTest {

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "-PT30M"})
  void rejectsDurationThatIsNotPositive(String duration) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ServiceType(1, "Oil change", Duration.parse(duration)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT1M", "PT8H"})
  void acceptsPositiveDuration(String duration) {
    assertThatNoException()
        .isThrownBy(() -> new ServiceType(1, "Oil change", Duration.parse(duration)));
  }
}
