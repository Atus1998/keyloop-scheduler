package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.sql.SQLException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SqlStatesTest {

  @Test
  void findsStateDeepInCauseChain() {
    var error =
        new RuntimeException(
            "outer", new IllegalStateException("middle", new SQLException("db", "23P01")));

    assertThat(SqlStates.of(error)).contains("23P01");
  }

  @Test
  void returnsEmptyWhenNoSqlException() {
    assertThat(SqlStates.of(new RuntimeException("no database here"))).isEmpty();
  }

  @Test
  void stopsOnCauseCycle() {
    var first = new RuntimeException("first");
    var second = new RuntimeException("second");
    first.initCause(second);
    second.initCause(first);

    assertTimeoutPreemptively(
        Duration.ofSeconds(1), () -> assertThat(SqlStates.of(first)).isEmpty());
  }

  @Test
  void skipsSqlExceptionWithNullState() {
    var error = new SQLException("no state", new SQLException("deadlock", "40P01"));

    assertThat(SqlStates.of(error)).contains("40P01");
  }
}
