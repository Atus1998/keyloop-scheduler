package com.keyloop.scheduler.domain;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** All bays are equal, so the lowest free id wins. */
public final class BayPicker {

  private BayPicker() {}

  public static OptionalLong pick(List<Long> freeBayIds) {
    Objects.requireNonNull(freeBayIds, "freeBayIds");
    return freeBayIds.stream().mapToLong(Long::longValue).min();
  }
}
