package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.TransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class SpringTransactionAdapter implements TransactionPort {

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public <T> T inReadOnlyTransaction(Supplier<T> work) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
