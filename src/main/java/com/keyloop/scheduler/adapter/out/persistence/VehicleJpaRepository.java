package com.keyloop.scheduler.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read only: reference data comes from the seed. */
interface VehicleJpaRepository extends Repository<VehicleEntity, Long> {

  Optional<VehicleEntity> findById(long id);
}
