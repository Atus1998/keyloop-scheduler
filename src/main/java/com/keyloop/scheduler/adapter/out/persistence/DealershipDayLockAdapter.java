package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.DealershipDayLockPort;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Not implemented yet. */
@Component
class DealershipDayLockAdapter implements DealershipDayLockPort {

  private final JdbcTemplate jdbc;

  DealershipDayLockAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void lock(long dealershipId, LocalDate day) {
    // Not implemented yet.
  }

  static long lockKey(long dealershipId, LocalDate day) {
    return 0;
  }

  static RuntimeException translate(RuntimeException error) {
    return error;
  }
}
