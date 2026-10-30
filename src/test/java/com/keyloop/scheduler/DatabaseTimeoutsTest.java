package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Every pooled connection gives up on a lock or a statement after a while, so a replay that waits
 * for a stuck transaction cannot hold its connection forever ({@code docs/system-design.md},
 * section 6).
 */
@PostgresIntegrationTest
class DatabaseTimeoutsTest {

  private static final int CONNECTIONS_HELD = 3;

  @Autowired private DataSource dataSource;

  @Test
  void everyConnectionHasLockAndStatementTimeouts() throws SQLException {
    List<Connection> held = new ArrayList<>();
    try {
      for (int i = 0; i < CONNECTIONS_HELD; i++) {
        held.add(dataSource.getConnection());
      }
      for (Connection connection : held) {
        assertThat(show(connection, "lock_timeout")).isEqualTo("5s");
        assertThat(show(connection, "statement_timeout")).isEqualTo("30s");
      }
    } finally {
      for (Connection connection : held) {
        connection.close();
      }
    }
  }

  @Test
  void poolWaitsAtMostFiveSecondsForAConnection() {
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    HikariDataSource pool = (HikariDataSource) dataSource;

    assertThat(pool.getConnectionTimeout()).isEqualTo(5000);
    assertThat(pool.getValidationTimeout()).isEqualTo(2000);
  }

  private static String show(Connection connection, String setting) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SHOW " + setting)) {
      result.next();
      return result.getString(1);
    }
  }
}
