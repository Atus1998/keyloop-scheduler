package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.DealershipDayLockPort;
import com.keyloop.scheduler.application.service.BookingQueueTimeoutException;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

/**
 * Takes a PostgreSQL transaction-level advisory lock through {@code lock_booking_day} (migration
 * V2), which waits at most 2 s. JdbcTemplate joins the open JPA transaction, so the lock ends with
 * it.
 */
@Component
class DealershipDayLockAdapter implements DealershipDayLockPort {

  /** Days take the low 21 bits: about 5,700 years before two days share a key. */
  private static final int DAY_BITS = 21;

  private static final long DAYS_PER_CYCLE = 1L << DAY_BITS;

  private static final ResultSetExtractor<Void> IGNORE_RESULT = resultSet -> null;

  private final JdbcTemplate jdbc;

  DealershipDayLockAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void lock(long dealershipId, LocalDate day) {
    long key = lockKey(dealershipId, day);
    try {
      jdbc.query("SELECT lock_booking_day(?)", IGNORE_RESULT, key);
    } catch (RuntimeException e) {
      throw translate(e);
    }
  }

  /**
   * One bigint per dealership and day. Ids from 2^42 on wrap and may share a key with another
   * dealership; that only makes two lines wait for each other, and the constraints still stop any
   * double booking.
   */
  static long lockKey(long dealershipId, LocalDate day) {
    Objects.requireNonNull(day, "day");
    return (dealershipId << DAY_BITS) + Math.floorMod(day.toEpochDay(), DAYS_PER_CYCLE);
  }

  /** 55P03 (the lock wait ran out) becomes {@link BookingQueueTimeoutException}. */
  static RuntimeException translate(RuntimeException error) {
    return SqlStates.of(error)
        .filter(SqlStates.LOCK_NOT_AVAILABLE::equals)
        .<RuntimeException>map(state -> new BookingQueueTimeoutException(error))
        .orElse(error);
  }
}
