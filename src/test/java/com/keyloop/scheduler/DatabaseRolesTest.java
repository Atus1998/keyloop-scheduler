package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import com.keyloop.scheduler.testsupport.TestClockConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Two database users, set up by the same init script Docker Compose mounts: Flyway migrates as
 * {@code scheduler_migrator}, the app runs as {@code scheduler_app} with data access only.
 * Passwords are made up per run, so none is stored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfiguration.class)
@DirtiesContext
class DatabaseRolesTest {

  private static final String INIT_SCRIPT = "docker/postgres/initdb/01-roles.sh";
  private static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();
  private static final String APP_PASSWORD = UUID.randomUUID().toString();
  private static final String INSUFFICIENT_PRIVILEGE = "42501";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
          .withEnv("SCHEDULER_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
          .withEnv("SCHEDULER_APP_PASSWORD", APP_PASSWORD)
          .withCopyFileToContainer(
              MountableFile.forHostPath(INIT_SCRIPT, 0755),
              "/docker-entrypoint-initdb.d/01-roles.sh");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void databaseUsers(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "scheduler_app");
    registry.add("spring.datasource.password", () -> APP_PASSWORD);
    registry.add("spring.flyway.user", () -> "scheduler_migrator");
    registry.add("spring.flyway.password", () -> MIGRATOR_PASSWORD);
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvcTester mvc;
  @Autowired private Flyway flyway;

  @Test
  void appRunsAsTheDataOnlyUser() {
    assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("scheduler_app");
  }

  /** Flyway connects as another user, outside the pool; the app's own connections keep both. */
  @Test
  void appConnectionsHaveLockAndStatementTimeouts() {
    assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("5s");
    assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("30s");
  }

  /** afterMigrate.sql also runs when there is nothing new to migrate, as on every restart. */
  @Test
  void historyRightsAreTakenBackOnAMigrateWithNothingNew() throws SQLException {
    asSuperuserExecute("GRANT SELECT ON flyway_schema_history TO scheduler_app");

    var result = flyway.migrate();

    assertThat(result.migrationsExecuted).isZero();
    Throwable error =
        catchThrowable(() -> jdbc.execute("SELECT count(*) FROM flyway_schema_history"));
    assertThat(sqlState(error)).isEqualTo(INSUFFICIENT_PRIVILEGE);
  }

  @Test
  void flywayMigratesAsTheMigrationUser() throws SQLException {
    assertThat(asSuperuser("SELECT DISTINCT installed_by FROM flyway_schema_history"))
        .containsExactly("scheduler_migrator");
    assertThat(asSuperuser("SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = 'public'"))
        .containsExactly("scheduler_migrator");
    assertThat(asSuperuser("SELECT extname FROM pg_extension WHERE extname = 'btree_gist'"))
        .containsExactly("btree_gist");
  }

  @Test
  void appCanReadAndWriteDataAndBook() {
    var fixtures = new PersistenceFixtures(jdbc);
    long dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    long serviceTypeId = fixtures.insertServiceType("Roles check", 90);
    long technicianId = fixtures.insertTechnician(dealershipId, "Roles");
    fixtures.insertSkill(technicianId, serviceTypeId);
    fixtures.insertBay(dealershipId);
    long vehicleId = fixtures.insertVehicle(fixtures.insertCustomer(), "RL01 ABC");

    var result =
        mvc.post()
            .uri("/appointments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"dealershipId": %d, "vehicleId": %d, "serviceTypeId": %d,
                 "startTime": "2030-10-22T08:00:00Z"}
                """
                    .formatted(dealershipId, vehicleId, serviceTypeId))
            .exchange();

    assertThat(result).hasStatus(HttpStatus.CREATED);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE TABLE intruder (id int)",
        "ALTER TABLE appointment ADD COLUMN intruder int",
        "DROP TABLE appointment",
        "TRUNCATE appointment",
        "DELETE FROM flyway_schema_history",
        "UPDATE flyway_schema_history SET success = false",
        "SELECT count(*) FROM flyway_schema_history"
      })
  void appCannotChangeSchemaOrMigrationHistory(String sql) {
    Throwable error = catchThrowable(() -> jdbc.execute(sql));

    assertThat(sqlState(error)).as(sql).isEqualTo(INSUFFICIENT_PRIVILEGE);
  }

  /** Reads with the container's superuser, which may see what the app may not. */
  private static List<String> asSuperuser(String sql) throws SQLException {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      List<String> values = new ArrayList<>();
      while (rows.next()) {
        values.add(rows.getString(1));
      }
      return List.copyOf(values);
    }
  }

  private static void asSuperuserExecute(String sql) throws SQLException {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static String sqlState(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlError && sqlError.getSQLState() != null) {
        return sqlError.getSQLState();
      }
    }
    return null;
  }
}
