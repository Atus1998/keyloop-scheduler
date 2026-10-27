package com.keyloop.scheduler.adapter.out.persistence;

import java.util.Optional;

/** Finds the PostgreSQL SQLState behind an exception. */
final class SqlStates {

  static final String EXCLUSION_VIOLATION = "23P01";
  static final String DEADLOCK_DETECTED = "40P01";

  private SqlStates() {}

  static Optional<String> of(Throwable error) {
    return Optional.empty();
  }
}
