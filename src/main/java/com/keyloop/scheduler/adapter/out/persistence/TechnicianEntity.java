package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.domain.Technician;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "technician")
class TechnicianEntity {

  @Id private Long id;

  @Column(name = "dealership_id", nullable = false)
  private long dealershipId;

  @Column(nullable = false)
  private String name;

  protected TechnicianEntity() {}

  Technician toDomain() {
    return new Technician(id, dealershipId, name);
  }
}
