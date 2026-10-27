package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "appointment")
class AppointmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "dealership_id", nullable = false)
  private long dealershipId;

  @Column(name = "vehicle_id", nullable = false)
  private long vehicleId;

  @Column(name = "service_type_id", nullable = false)
  private long serviceTypeId;

  @Column(name = "technician_id", nullable = false)
  private long technicianId;

  @Column(name = "bay_id", nullable = false)
  private long bayId;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AppointmentStatus status;

  protected AppointmentEntity() {}

  /** A new, not yet stored row. The database assigns the id. */
  static AppointmentEntity newRow(Appointment appointment) {
    var entity = new AppointmentEntity();
    entity.dealershipId = appointment.dealershipId();
    entity.vehicleId = appointment.vehicleId();
    entity.serviceTypeId = appointment.serviceTypeId();
    entity.technicianId = appointment.technicianId();
    entity.bayId = appointment.bayId();
    // PostgreSQL keeps microseconds. Truncate here so the returned appointment matches the row.
    entity.startAt = appointment.startAt().truncatedTo(ChronoUnit.MICROS);
    entity.endAt = appointment.endAt().truncatedTo(ChronoUnit.MICROS);
    entity.status = appointment.status();
    return entity;
  }

  Appointment toDomain() {
    return new Appointment(
        id, dealershipId, vehicleId, serviceTypeId, technicianId, bayId, startAt, endAt, status);
  }
}
