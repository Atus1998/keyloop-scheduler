package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.keyloop.scheduler.PostgresIntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Constraints that no adapter can break, checked with plain SQL. */
@PostgresIntegrationTest
class SchemaConstraintsTest {

  private static final String CHECK_VIOLATION = "23514";
  private static final String FOREIGN_KEY_VIOLATION = "23503";
  private static final String UNIQUE_VIOLATION = "23505";
  private static final long UNKNOWN_ID = 999_999;

  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
  }

  @Test
  void rejectsDealershipClosingAtOpeningTime() {
    assertRejected(
        () -> fixtures.insertDealership("Asia/Ho_Chi_Minh", "09:00", "09:00"),
        CHECK_VIOLATION,
        "dealership_hours_valid");
  }

  @Test
  void rejectsDealershipClosingBeforeOpening() {
    assertRejected(
        () -> fixtures.insertDealership("Asia/Ho_Chi_Minh", "17:00", "08:00"),
        CHECK_VIOLATION,
        "dealership_hours_valid");
  }

  @Test
  void rejectsUnknownAppointmentStatus() {
    var row = new AppointmentRow();

    assertRejected(
        () -> row.insert("status", "'PENDING'"), CHECK_VIOLATION, "appointment_status_valid");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"dealership_id", "vehicle_id", "service_type_id", "technician_id", "bay_id"})
  void rejectsAppointmentWithUnknownReference(String column) {
    var row = new AppointmentRow();

    assertRejected(
        () -> row.insert(column, String.valueOf(UNKNOWN_ID)), FOREIGN_KEY_VIOLATION, column);
  }

  @Test
  void rejectsTechnicianWithUnknownDealership() {
    assertRejected(
        () -> fixtures.insertTechnician(UNKNOWN_ID, "Nobody"),
        FOREIGN_KEY_VIOLATION,
        "technician_dealership_id_fkey");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 481})
  void rejectsServiceTypeWithDuration(int minutes) {
    assertRejected(
        () -> fixtures.insertServiceType("Out of range", minutes),
        CHECK_VIOLATION,
        "service_type_duration_valid");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 480})
  void acceptsServiceTypeWithDuration(int minutes) {
    long id = fixtures.insertServiceType("In range", minutes);

    assertThat(id).isPositive();
  }

  @Test
  void rejectsDuplicateVehicleRegistration() {
    long customerId = fixtures.insertCustomer();
    fixtures.insertVehicle(customerId, "51A-123.45");

    assertRejected(
        () -> fixtures.insertVehicle(customerId, "51A-123.45"),
        UNIQUE_VIOLATION,
        "vehicle_registration_unique");
  }

  @Test
  void rejectsAppointmentWithTechnicianFromOtherDealership() {
    var row = new AppointmentRow();
    long otherTechnician =
        fixtures.insertTechnician(fixtures.insertDealership(), "Other dealership");

    assertRejected(
        () -> row.insert("technician_id", String.valueOf(otherTechnician)),
        FOREIGN_KEY_VIOLATION,
        "appointment_technician_dealership_fkey");
  }

  @Test
  void rejectsAppointmentWithBayFromOtherDealership() {
    var row = new AppointmentRow();
    long otherBay = fixtures.insertBay(fixtures.insertDealership());

    assertRejected(
        () -> row.insert("bay_id", String.valueOf(otherBay)),
        FOREIGN_KEY_VIOLATION,
        "appointment_bay_dealership_fkey");
  }

  private static void assertRejected(ThrowingCallable insert, String sqlState, String detail) {
    Throwable thrown = catchThrowable(insert);

    assertThat(thrown).isNotNull();
    assertThat(SqlStates.of(thrown)).contains(sqlState);
    assertThat(thrown).hasStackTraceContaining(detail);
  }

  /** A valid appointment insert where one column can be replaced by a raw SQL value. */
  private final class AppointmentRow {

    private final Map<String, String> values = new LinkedHashMap<>();

    AppointmentRow() {
      long dealershipId = fixtures.insertDealership();
      long customerId = fixtures.insertCustomer();
      values.put("dealership_id", String.valueOf(dealershipId));
      values.put("vehicle_id", String.valueOf(fixtures.insertVehicle(customerId, "51A-999.99")));
      values.put("service_type_id", String.valueOf(fixtures.insertServiceType("Oil change", 60)));
      values.put("technician_id", String.valueOf(fixtures.insertTechnician(dealershipId, "Tech")));
      values.put("bay_id", String.valueOf(fixtures.insertBay(dealershipId)));
      values.put("start_at", "'2026-11-03T10:00:00Z'");
      values.put("end_at", "'2026-11-03T11:00:00Z'");
      values.put("status", "'CONFIRMED'");
    }

    void insert(String column, String rawValue) {
      var row = new LinkedHashMap<>(values);
      row.put(column, rawValue);
      jdbc.update(
          "INSERT INTO appointment ("
              + String.join(", ", row.keySet())
              + ") VALUES ("
              + String.join(", ", row.values())
              + ")");
    }
  }
}
