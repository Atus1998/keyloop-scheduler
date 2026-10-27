package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.domain.Dealership;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;

@Entity
@Table(name = "dealership")
class DealershipEntity {

  @Id private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "zone_id", nullable = false)
  private String zoneId;

  @Column(name = "opens_at", nullable = false)
  private LocalTime opensAt;

  @Column(name = "closes_at", nullable = false)
  private LocalTime closesAt;

  protected DealershipEntity() {}

  Dealership toDomain() {
    return new Dealership(id, name, zone(), opensAt, closesAt);
  }

  private ZoneId zone() {
    try {
      return ZoneId.of(zoneId);
    } catch (DateTimeException e) {
      throw new IllegalStateException("Dealership " + id + " has an invalid zone_id: " + zoneId, e);
    }
  }
}
