package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.out.DealershipDayLockPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.application.service.BookingQueueTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The dealership-day lock on a real PostgreSQL. Advisory locks need no rows, so the ids are plain
 * numbers. Another thread holds a lock in an open transaction while the test asks for one.
 */
@PostgresIntegrationTest
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DealershipDayLockAdapterTest {

  private static final long DEALERSHIP = 1;
  private static final LocalDate DAY = LocalDate.of(2030, 10, 22);
  private static final String OWN_ADVISORY_LOCKS =
      "SELECT count(*) FROM pg_locks"
          + " WHERE locktype = 'advisory' AND granted AND pid = pg_backend_pid()";

  @Autowired private DealershipDayLockPort locks;
  @Autowired private TransactionPort transactions;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void timesOutWhenAnotherTransactionHoldsTheSameDealershipAndDay() throws Exception {
    try (Holder holder = hold(DEALERSHIP, DAY)) {
      assertThatThrownBy(() -> lockInTransaction(DEALERSHIP, DAY))
          .isInstanceOf(BookingQueueTimeoutException.class);
    }
  }

  @ParameterizedTest
  @CsvSource({"2, 2030-10-22", "1, 2030-10-23"})
  void doesNotWaitForAnotherDealershipOrDay(long dealership, LocalDate day) throws Exception {
    try (Holder holder = hold(DEALERSHIP, DAY)) {
      assertThatCode(() -> lockInTransaction(dealership, day)).doesNotThrowAnyException();
    }
  }

  @Test
  void holdsTheLockUntilTheTransactionEnds() {
    long heldInside =
        transactions.inTransaction(
            () -> {
              locks.lock(DEALERSHIP, DAY);
              return jdbc.queryForObject(OWN_ADVISORY_LOCKS, Long.class);
            });

    assertThat(heldInside).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'", Long.class))
        .isZero();
  }

  @Test
  void keepsTheConnectionLockTimeoutAfterLocking() {
    String after =
        transactions.inTransaction(
            () -> {
              locks.lock(DEALERSHIP, DAY);
              return jdbc.queryForObject("SHOW lock_timeout", String.class);
            });

    assertThat(after).isEqualTo("5s");
  }

  /** The function's own 2 s limit, not the connection's 5 s, ends the wait. */
  @Test
  void waitsAboutTwoSecondsBeforeTimingOut() throws Exception {
    try (Holder holder = hold(DEALERSHIP, DAY)) {
      long started = System.nanoTime();

      assertThatThrownBy(() -> lockInTransaction(DEALERSHIP, DAY))
          .isInstanceOf(BookingQueueTimeoutException.class);

      assertThat(Duration.ofNanos(System.nanoTime() - started))
          .isBetween(Duration.ofMillis(1500), Duration.ofMillis(4000));
    }
  }

  @Test
  void refusesToLockOutsideATransaction() {
    assertThatThrownBy(() -> locks.lock(DEALERSHIP, DAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("open transaction");
  }

  private void lockInTransaction(long dealership, LocalDate day) {
    transactions.inTransaction(
        () -> {
          locks.lock(dealership, day);
          return null;
        });
  }

  /** Locks on another thread and keeps the transaction open until closed. */
  private Holder hold(long dealership, LocalDate day) throws Exception {
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> work =
        executor.submit(
            () ->
                transactions.inTransaction(
                    () -> {
                      locks.lock(dealership, day);
                      held.countDown();
                      await(release);
                      return null;
                    }));
    if (!held.await(10, TimeUnit.SECONDS)) {
      executor.shutdownNow();
      throw new AssertionError("The holder did not lock within 10 s: " + work);
    }
    return new Holder(release, work, executor);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Not released within 20 s");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding the lock", e);
    }
  }

  private record Holder(CountDownLatch release, Future<?> work, ExecutorService executor)
      implements AutoCloseable {

    @Override
    public void close() throws Exception {
      release.countDown();
      try {
        work.get(10, TimeUnit.SECONDS);
      } finally {
        executor.shutdownNow();
      }
    }
  }
}
