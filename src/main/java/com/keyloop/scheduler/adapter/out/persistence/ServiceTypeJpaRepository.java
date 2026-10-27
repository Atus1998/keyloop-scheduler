package com.keyloop.scheduler.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read only: reference data comes from the seed. */
interface ServiceTypeJpaRepository extends Repository<ServiceTypeEntity, Long> {

  Optional<ServiceTypeEntity> findById(long id);
}
