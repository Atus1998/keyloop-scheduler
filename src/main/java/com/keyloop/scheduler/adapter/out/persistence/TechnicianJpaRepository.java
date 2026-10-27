package com.keyloop.scheduler.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read only: reference data comes from the seed. */
interface TechnicianJpaRepository extends Repository<TechnicianEntity, Long> {

  @Query(
      value =
          """
          SELECT t.* FROM technician t
          JOIN technician_skill s ON s.technician_id = t.id
          WHERE t.dealership_id = :dealershipId AND s.service_type_id = :serviceTypeId
          ORDER BY t.id
          """,
      nativeQuery = true)
  List<TechnicianEntity> findQualified(
      @Param("dealershipId") long dealershipId, @Param("serviceTypeId") long serviceTypeId);
}
