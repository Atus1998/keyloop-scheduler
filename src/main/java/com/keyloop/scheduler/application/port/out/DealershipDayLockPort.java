package com.keyloop.scheduler.application.port.out;

import java.time.LocalDate;

/**
 * Makes bookings for one dealership and one local day run one at a time ({@code
 * docs/system-design.md}, section 6). Call it first in a read-write transaction: it waits for the
 * transaction that holds the same dealership and day, and the lock ends with the transaction.
 */
public interface DealershipDayLockPort {

  /**
   * Waits for the dealership and day.
   *
   * @throws com.keyloop.scheduler.application.service.BookingQueueTimeoutException when the wait
   *     takes longer than the limit
   */
  void lock(long dealershipId, LocalDate day);
}
