package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.TransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs application work in a transaction at the default isolation (READ COMMITTED on PostgreSQL).
 * {@link TransactionTemplate} rolls back on any runtime exception and rethrows it unchanged.
 */
@Component
class SpringTransactionAdapter implements TransactionPort {

  private final TransactionTemplate readWrite;
  private final TransactionTemplate readOnly;

  SpringTransactionAdapter(PlatformTransactionManager transactionManager) {
    this.readWrite = new TransactionTemplate(transactionManager);
    this.readOnly = new TransactionTemplate(transactionManager);
    this.readOnly.setReadOnly(true);
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return readWrite.execute(status -> work.get());
  }

  @Override
  public <T> T inReadOnlyTransaction(Supplier<T> work) {
    return readOnly.execute(status -> work.get());
  }
}
