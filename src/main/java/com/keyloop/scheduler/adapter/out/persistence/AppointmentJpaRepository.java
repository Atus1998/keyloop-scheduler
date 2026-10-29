package com.keyloop.scheduler.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, Long> {

  /**
   * At most one row: {@code appointment_vehicle_no_overlap} allows one CONFIRMED row per vehicle
   * and start.
   */
  @Query(
      value =
          """
          SELECT id, dealership_id, vehicle_id, service_type_id, technician_id, bay_id,
                 start_at, end_at, status
          FROM appointment
          WHERE dealership_id = :dealershipId AND vehicle_id = :vehicleId
            AND service_type_id = :serviceTypeId AND start_at = :startAt
            AND status = 'CONFIRMED'
          """,
      nativeQuery = true)
  Optional<AppointmentEntity> findConfirmedRetryMatch(
      @Param("dealershipId") long dealershipId,
      @Param("vehicleId") long vehicleId,
      @Param("serviceTypeId") long serviceTypeId,
      @Param("startAt") Instant startAt);
}
