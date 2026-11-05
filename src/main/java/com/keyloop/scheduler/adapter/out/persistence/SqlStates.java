package com.keyloop.scheduler.adapter.out.persistence;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the PostgreSQL SQLState behind an exception. Spring and Hibernate wrap database errors in
 * classes that change between versions, so only the SQLState is trusted.
 */
final class SqlStates {

  static final String EXCLUSION_VIOLATION = "23P01";
  static final String DEADLOCK_DETECTED = "40P01";
  static final String LOCK_NOT_AVAILABLE = "55P03";

  private SqlStates() {}

  /** The first non-null SQLState in the cause chain. */
  static Optional<String> of(Throwable error) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = error; current != null && seen.add(current); ) {
      if (current instanceof SQLException sql && sql.getSQLState() != null) {
        return Optional.of(sql.getSQLState());
      }
      current = current.getCause();
    }
    return Optional.empty();
  }
}
