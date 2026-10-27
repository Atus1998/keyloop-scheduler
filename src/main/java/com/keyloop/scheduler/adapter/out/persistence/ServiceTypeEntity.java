package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.domain.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;

@Entity
@Table(name = "service_type")
class ServiceTypeEntity {

  @Id private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  protected ServiceTypeEntity() {}

  ServiceType toDomain() {
    return new ServiceType(id, name, Duration.ofMinutes(durationMinutes));
  }
}
