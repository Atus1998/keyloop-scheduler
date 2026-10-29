package com.keyloop.scheduler.application.port.out;

import java.util.function.Supplier;

/**
 * Runs work in a database transaction. A runtime exception from {@code work} rolls the transaction
 * back and is rethrown unchanged.
 */
public interface TransactionPort {

  <T> T inTransaction(Supplier<T> work);

  <T> T inReadOnlyTransaction(Supplier<T> work);
}
