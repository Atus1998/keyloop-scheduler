package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@PostgresIntegrationTest
class SpringTransactionAdapterTest {

  @Autowired private TransactionPort transactions;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
  }

  @Test
  void commitsWorkAndReturnsItsResult() {
    long id = transactions.inTransaction(() -> fixtures.insertServiceType("Oil change", 30));

    assertThat(countServiceTypes()).isEqualTo(1);
    assertThat(id).isPositive();
  }

  @Test
  void rollsBackAndRethrowsSameExceptionWhenWorkFails() {
    var failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                transactions.inTransaction(
                    () -> {
                      fixtures.insertServiceType("Oil change", 30);
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(countServiceTypes()).isZero();
  }

  @Test
  void runsWorkInReadWriteTransaction() {
    boolean[] state =
        transactions.inTransaction(
            () ->
                new boolean[] {
                  TransactionSynchronizationManager.isActualTransactionActive(),
                  TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                });

    assertThat(state).containsExactly(true, false);
  }

  @Test
  void runsReadOnlyWorkInReadOnlyTransaction() {
    boolean[] state =
        transactions.inReadOnlyTransaction(
            () ->
                new boolean[] {
                  TransactionSynchronizationManager.isActualTransactionActive(),
                  TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                });

    assertThat(state).containsExactly(true, true);
  }

  private int countServiceTypes() {
    return jdbc.queryForObject("SELECT count(*) FROM service_type", Integer.class);
  }
}
