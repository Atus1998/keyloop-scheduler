package com.keyloop.scheduler.domain;

import java.util.Objects;

public record Technician(long id, long dealershipId, String name) {

  public Technician {
    Objects.requireNonNull(name, "name");
  }
}
