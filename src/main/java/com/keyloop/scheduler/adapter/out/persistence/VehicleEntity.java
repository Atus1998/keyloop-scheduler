package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.domain.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle")
class VehicleEntity {

  @Id private Long id;

  @Column(name = "customer_id", nullable = false)
  private long customerId;

  @Column(nullable = false)
  private String registration;

  protected VehicleEntity() {}

  Vehicle toDomain() {
    return new Vehicle(id, customerId, registration);
  }
}
