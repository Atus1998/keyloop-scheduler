package com.keyloop.scheduler.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Availability queries. {@code start_at < :endAt AND end_at > :startAt} is the {@code &&} test of
 * the {@code [start, end)} ranges in the exclusion constraints, so back-to-back rows do not
 * overlap. Only CONFIRMED rows block, as in the constraints.
 */
interface AvailabilityJpaRepository extends Repository<AppointmentEntity, Long> {

  /** Every dealership: the vehicle constraint is global. */
  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1 FROM appointment
            WHERE vehicle_id = :vehicleId AND status = 'CONFIRMED'
              AND start_at < :endAt AND end_at > :startAt)
          """,
      nativeQuery = true)
  boolean isVehicleBusy(
      @Param("vehicleId") long vehicleId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt);

  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1 FROM appointment
            WHERE technician_id = :technicianId AND status = 'CONFIRMED'
              AND start_at < :endAt AND end_at > :startAt)
          """,
      nativeQuery = true)
  boolean isTechnicianBusy(
      @Param("technicianId") long technicianId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt);

  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1 FROM appointment
            WHERE bay_id = :bayId AND status = 'CONFIRMED'
              AND start_at < :endAt AND end_at > :startAt)
          """,
      nativeQuery = true)
  boolean isBayBusy(
      @Param("bayId") long bayId, @Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

  @Query(
      value =
          """
          SELECT t.id AS "technicianId",
                 (SELECT count(*) FROM appointment d
                  WHERE d.technician_id = t.id AND d.status = 'CONFIRMED'
                    AND d.start_at >= :dayStart AND d.start_at < :dayEnd) AS "confirmedThatDay"
          FROM technician t
          JOIN technician_skill s ON s.technician_id = t.id
          WHERE t.dealership_id = :dealershipId AND s.service_type_id = :serviceTypeId
            AND NOT EXISTS (
              SELECT 1 FROM appointment a
              WHERE a.technician_id = t.id AND a.status = 'CONFIRMED'
                AND a.start_at < :endAt AND a.end_at > :startAt)
          ORDER BY t.id
          """,
      nativeQuery = true)
  List<FreeTechnicianRow> findFreeQualifiedTechnicians(
      @Param("dealershipId") long dealershipId,
      @Param("serviceTypeId") long serviceTypeId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt,
      @Param("dayStart") Instant dayStart,
      @Param("dayEnd") Instant dayEnd);

  @Query(
      value =
          """
          SELECT b.id FROM service_bay b
          WHERE b.dealership_id = :dealershipId
            AND NOT EXISTS (
              SELECT 1 FROM appointment a
              WHERE a.bay_id = b.id AND a.status = 'CONFIRMED'
                AND a.start_at < :endAt AND a.end_at > :startAt)
          ORDER BY b.id
          """,
      nativeQuery = true)
  List<Long> findFreeBayIds(
      @Param("dealershipId") long dealershipId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt);
}
