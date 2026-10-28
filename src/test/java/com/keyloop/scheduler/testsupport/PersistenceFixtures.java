package com.keyloop.scheduler.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts test rows with plain SQL, for any test on a real database. Tests do not load the seed
 * data.
 */
public final class PersistenceFixtures {

  public static final String DEALERSHIP_NAME = "Test dealership";

  private final JdbcTemplate jdbc;

  public PersistenceFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void truncateAll() {
    jdbc.execute(
        """
        TRUNCATE appointment, technician_skill, vehicle, customer, service_type,
                 technician, service_bay, dealership RESTART IDENTITY CASCADE
        """);
  }

  public long insertDealership(String zoneId, String opensAt, String closesAt) {
    return insertReturningId(
        "INSERT INTO dealership (name, zone_id, opens_at, closes_at)"
            + " VALUES (?, ?, ?::time, ?::time) RETURNING id",
        DEALERSHIP_NAME,
        zoneId,
        opensAt,
        closesAt);
  }

  public long insertDealership() {
    return insertDealership("Asia/Ho_Chi_Minh", "08:00", "17:00");
  }

  public long insertBay(long dealershipId) {
    return insertReturningId(
        "INSERT INTO service_bay (dealership_id, name) VALUES (?, 'Bay') RETURNING id",
        dealershipId);
  }

  public long insertTechnician(long dealershipId, String name) {
    return insertReturningId(
        "INSERT INTO technician (dealership_id, name) VALUES (?, ?) RETURNING id",
        dealershipId,
        name);
  }

  public long insertServiceType(String name, int durationMinutes) {
    return insertReturningId(
        "INSERT INTO service_type (name, duration_minutes) VALUES (?, ?) RETURNING id",
        name,
        durationMinutes);
  }

  public void insertSkill(long technicianId, long serviceTypeId) {
    jdbc.update(
        "INSERT INTO technician_skill (technician_id, service_type_id) VALUES (?, ?)",
        technicianId,
        serviceTypeId);
  }

  public long insertCustomer() {
    return insertReturningId("INSERT INTO customer (name) VALUES ('Test customer') RETURNING id");
  }

  public long insertVehicle(long customerId, String registration) {
    return insertReturningId(
        "INSERT INTO vehicle (customer_id, registration) VALUES (?, ?) RETURNING id",
        customerId,
        registration);
  }

  public int countAppointments() {
    return jdbc.queryForObject("SELECT count(*) FROM appointment", Integer.class);
  }

  private long insertReturningId(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }
}
