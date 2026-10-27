package com.keyloop.scheduler.domain;

import java.time.Duration;
import java.util.Objects;

public record ServiceType(long id, String name, Duration duration) {

  public ServiceType {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(duration, "duration");
  }
}
