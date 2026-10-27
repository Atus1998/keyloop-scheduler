package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class MigrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void createsAllEightTables() {
    var tables =
        jdbc.queryForList(
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
              AND table_name <> 'flyway_schema_history'
            """,
            String.class);

    assertThat(tables)
        .containsExactlyInAnyOrder(
            "dealership",
            "service_bay",
            "technician",
            "service_type",
            "technician_skill",
            "customer",
            "vehicle",
            "appointment");
  }

  @Test
  void installsBtreeGist() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

    assertThat(count).isEqualTo(1);
  }

  @Test
  void appliesAllMigrationsSuccessfully() {
    Integer applied =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE script = 'V1__schema.sql' AND success",
            Integer.class);
    Integer failed =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);

    assertThat(applied).isEqualTo(1);
    assertThat(failed).isZero();
  }

  @Test
  void createsThreeExclusionConstraints() {
    var constraints =
        jdbc.queryForList(
            """
            SELECT conname FROM pg_constraint
            WHERE contype = 'x' AND conrelid = 'appointment'::regclass
            """,
            String.class);

    assertThat(constraints)
        .containsExactlyInAnyOrder(
            "appointment_technician_no_overlap",
            "appointment_bay_no_overlap",
            "appointment_vehicle_no_overlap");
  }

  @Test
  void doesNotLoadSeedInTests() {
    Integer seedRuns =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE script LIKE 'R\\_\\_%'",
            Integer.class);

    assertThat(seedRuns).isZero();
  }
}
