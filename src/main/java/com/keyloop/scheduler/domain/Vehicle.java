package com.keyloop.scheduler.domain;

import java.util.Objects;

public record Vehicle(long id, long customerId, String registration) {

  public Vehicle {
    Objects.requireNonNull(registration, "registration");
  }
}
