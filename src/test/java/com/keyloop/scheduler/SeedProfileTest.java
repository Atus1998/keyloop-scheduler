package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The app's own Flyway locations without the {@code demo} profile: the seed is demo data and must
 * not load anywhere else. Same annotations as {@link SchedulerApplicationTests}, so they share a
 * context.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeedProfileTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void loadsNoSeedWithoutDemoProfile() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM dealership", Integer.class)).isZero();
  }

  @Test
  void runsNoRepeatableSeedMigrationWithoutDemoProfile() {
    Integer seedRuns =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE script LIKE 'R\\_\\_%'",
            Integer.class);

    assertThat(seedRuns).isZero();
  }
}
