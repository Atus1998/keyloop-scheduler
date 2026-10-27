package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.domain.Dealership;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Uses the app's own Flyway locations, so the seed is loaded. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeedDataTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ReferenceDataPort referenceData;

  @Test
  void loadsSeedCounts() {
    assertThat(count("dealership")).isEqualTo(2);
    assertThat(count("service_bay")).isEqualTo(6);
    assertThat(count("technician")).isEqualTo(6);
    assertThat(count("service_type")).isEqualTo(4);
    assertThat(count("customer")).isEqualTo(2);
    assertThat(count("vehicle")).isEqualTo(4);
  }

  @Test
  void seedDealershipsHaveExpectedZonesAndHours() {
    assertThat(referenceData.findDealership(1))
        .contains(
            new Dealership(
                1,
                "Saigon Central",
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 0),
                LocalTime.of(17, 0)));
    assertThat(referenceData.findDealership(2))
        .contains(
            new Dealership(
                2,
                "London West",
                ZoneId.of("Europe/London"),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)));
  }

  @Test
  void everyDealershipOffersEveryServiceType() {
    Integer missing =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM dealership d CROSS JOIN service_type s
            WHERE NOT EXISTS (
              SELECT 1 FROM technician t
              JOIN technician_skill k ON k.technician_id = t.id
              WHERE t.dealership_id = d.id AND k.service_type_id = s.id)
            """,
            Integer.class);

    assertThat(missing).isZero();
  }

  @Test
  void nextIdsDoNotCollideWithSeed() {
    Long customerId =
        jdbc.queryForObject(
            "INSERT INTO customer (name) VALUES ('Sequence check') RETURNING id", Long.class);
    Long serviceTypeId =
        jdbc.queryForObject(
            "INSERT INTO service_type (name, duration_minutes) VALUES ('Sequence check', 30)"
                + " RETURNING id",
            Long.class);
    try {
      assertThat(customerId).isGreaterThan(2);
      assertThat(serviceTypeId).isGreaterThan(4);
    } finally {
      jdbc.update("DELETE FROM customer WHERE id = ?", customerId);
      jdbc.update("DELETE FROM service_type WHERE id = ?", serviceTypeId);
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }
}
