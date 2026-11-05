package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.application.service.ResourceConflictException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

class AppointmentAdapterTranslationTest {

  @Test
  void mapsExclusionToExclusionKind() {
    var error = new DataIntegrityViolationException("x", new SQLException("overlap", "23P01"));

    RuntimeException translated = AppointmentAdapter.translate(error);

    assertThat(translated)
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            e -> assertThat(e.kind()).isEqualTo(ResourceConflictException.Kind.EXCLUSION))
        .hasCause(error);
  }

  @Test
  void mapsDeadlockToDeadlockKind() {
    var error = new CannotAcquireLockException("x", new SQLException("deadlock", "40P01"));

    RuntimeException translated = AppointmentAdapter.translate(error);

    assertThat(translated)
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            e -> assertThat(e.kind()).isEqualTo(ResourceConflictException.Kind.DEADLOCK))
        .hasCause(error);
  }

  /** 55P03 from an insert is not the booking queue: only the lock adapter maps it to 503. */
  @ParameterizedTest
  @ValueSource(strings = {"23514", "55P03"})
  void rethrowsOtherStates(String state) {
    var error = new DataIntegrityViolationException("x", new SQLException("other", state));

    assertThat(AppointmentAdapter.translate(error)).isSameAs(error);
  }
}
