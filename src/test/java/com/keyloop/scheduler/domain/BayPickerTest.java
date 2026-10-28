package com.keyloop.scheduler.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.junit.jupiter.api.Test;

class BayPickerTest {

  @Test
  void picksLowestFreeBayId() {
    assertThat(BayPicker.pick(List.of(7L, 3L, 5L))).hasValue(3);
  }

  @Test
  void returnsEmptyWhenNoBayIsFree() {
    assertThat(BayPicker.pick(List.of())).isEmpty();
  }

  @Test
  void rejectsNullListByName() {
    assertThatNullPointerException()
        .isThrownBy(() -> BayPicker.pick(null))
        .withMessage("freeBayIds");
  }
}
