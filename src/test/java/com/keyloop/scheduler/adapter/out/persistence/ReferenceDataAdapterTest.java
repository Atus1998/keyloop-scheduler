package com.keyloop.scheduler.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.domain.Dealership;
import com.keyloop.scheduler.domain.ServiceType;
import com.keyloop.scheduler.domain.Technician;
import com.keyloop.scheduler.domain.Vehicle;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class ReferenceDataAdapterTest {

  private static final long UNKNOWN_ID = 999_999;

  @Autowired private ReferenceDataPort referenceData;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
  }

  @Test
  void returnsEmptyForUnknownDealership() {
    assertThat(referenceData.findDealership(UNKNOWN_ID)).isEmpty();
  }

  @Test
  void returnsEmptyForUnknownVehicle() {
    assertThat(referenceData.findVehicle(UNKNOWN_ID)).isEmpty();
  }

  @Test
  void returnsEmptyForUnknownServiceType() {
    assertThat(referenceData.findServiceType(UNKNOWN_ID)).isEmpty();
  }

  @Test
  void returnsEmptyListForUnknownDealershipOrServiceType() {
    long dealershipId = fixtures.insertDealership();
    long serviceTypeId = fixtures.insertServiceType("Oil change", 30);
    long technicianId = fixtures.insertTechnician(dealershipId, "Technician");
    fixtures.insertSkill(technicianId, serviceTypeId);

    assertThat(referenceData.findQualifiedTechnicians(UNKNOWN_ID, serviceTypeId)).isEmpty();
    assertThat(referenceData.findQualifiedTechnicians(dealershipId, UNKNOWN_ID)).isEmpty();
  }

  @Test
  void mapsDealershipZoneAndLocalHours() {
    long id = fixtures.insertDealership("Europe/London", "09:00", "18:00");

    assertThat(referenceData.findDealership(id))
        .contains(
            new Dealership(
                id,
                PersistenceFixtures.DEALERSHIP_NAME,
                ZoneId.of("Europe/London"),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)));
  }

  @Test
  void mapsVehicle() {
    long customerId = fixtures.insertCustomer();
    long id = fixtures.insertVehicle(customerId, "AB12 CDE");

    assertThat(referenceData.findVehicle(id)).contains(new Vehicle(id, customerId, "AB12 CDE"));
  }

  @Test
  void mapsServiceTypeDuration() {
    long id = fixtures.insertServiceType("Brake service", 90);

    assertThat(referenceData.findServiceType(id))
        .contains(new ServiceType(id, "Brake service", Duration.ofMinutes(90)));
  }

  @Test
  void failsClearlyOnInvalidZoneId() {
    long id = fixtures.insertDealership("Mars/Olympus_Mons", "09:00", "18:00");

    assertThatThrownBy(() -> referenceData.findDealership(id))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Dealership " + id)
        .hasMessageContaining("Mars/Olympus_Mons");
  }

  @Test
  void returnsQualifiedTechniciansOrderedById() {
    long dealershipId = fixtures.insertDealership();
    long serviceTypeId = fixtures.insertServiceType("Oil change", 30);
    long first = fixtures.insertTechnician(dealershipId, "First");
    long unskilled = fixtures.insertTechnician(dealershipId, "Unskilled");
    long third = fixtures.insertTechnician(dealershipId, "Third");
    fixtures.insertSkill(third, serviceTypeId);
    fixtures.insertSkill(first, serviceTypeId);

    assertThat(referenceData.findQualifiedTechnicians(dealershipId, serviceTypeId))
        .containsExactly(
            new Technician(first, dealershipId, "First"),
            new Technician(third, dealershipId, "Third"))
        .noneMatch(technician -> technician.id() == unskilled);
  }

  @Test
  void excludesTechniciansFromOtherDealerships() {
    long dealershipId = fixtures.insertDealership();
    long otherDealershipId = fixtures.insertDealership();
    long serviceTypeId = fixtures.insertServiceType("Oil change", 30);
    long own = fixtures.insertTechnician(dealershipId, "Own");
    long other = fixtures.insertTechnician(otherDealershipId, "Other");
    fixtures.insertSkill(own, serviceTypeId);
    fixtures.insertSkill(other, serviceTypeId);

    assertThat(referenceData.findQualifiedTechnicians(dealershipId, serviceTypeId))
        .containsExactly(new Technician(own, dealershipId, "Own"));
  }
}
