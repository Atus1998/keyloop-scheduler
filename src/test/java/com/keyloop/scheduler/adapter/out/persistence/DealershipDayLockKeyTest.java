package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.application.service.BookingQueueTimeoutException;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

/** The lock key and the error translation of the dealership-day lock. No database. */
class DealershipDayLockKeyTest {

  private static final LocalDate DAY = LocalDate.of(2030, 10, 22);

  @Test
  void givesTheSameKeyForTheSameDealershipAndDay() {
    assertThat(DealershipDayLockAdapter.lockKey(7, DAY))
        .isEqualTo(DealershipDayLockAdapter.lockKey(7, LocalDate.of(2030, 10, 22)));
  }

  @Test
  void givesAnotherKeyForAnotherDealership() {
    assertThat(DealershipDayLockAdapter.lockKey(7, DAY))
        .isNotEqualTo(DealershipDayLockAdapter.lockKey(8, DAY));
  }

  @Test
  void givesAnotherKeyForEveryDayOfTheBookingWindow() {
    long first = DealershipDayLockAdapter.lockKey(7, DAY);

    for (int days = 1; days <= 90; days++) {
      assertThat(DealershipDayLockAdapter.lockKey(7, DAY.plusDays(days)))
          .as("day +%d", days)
          .isNotEqualTo(first);
    }
  }

  @Test
  void doesNotFailForTheLargestIds() {
    assertThat(DealershipDayLockAdapter.lockKey(Long.MAX_VALUE, DAY))
        .isNotEqualTo(DealershipDayLockAdapter.lockKey(Long.MAX_VALUE, DAY.plusDays(1)));
  }

  @Test
  void mapsLockTimeoutToBookingQueueTimeout() {
    var error =
        new CannotAcquireLockException("x", new SQLException("canceling statement", "55P03"));

    assertThat(DealershipDayLockAdapter.translate(error))
        .isInstanceOf(BookingQueueTimeoutException.class)
        .hasCause(error);
  }

  @ParameterizedTest
  @ValueSource(strings = {"23P01", "40P01", "23514", "57014"})
  void returnsOtherStatesAsTheyAre(String state) {
    var error = new DataIntegrityViolationException("x", new SQLException("other", state));

    assertThat(DealershipDayLockAdapter.translate(error)).isSameAs(error);
  }

  @Test
  void returnsErrorsWithoutStateAsTheyAre() {
    var error = new IllegalStateException("no SQL state");

    assertThat(DealershipDayLockAdapter.translate(error)).isSameAs(error);
  }
}
