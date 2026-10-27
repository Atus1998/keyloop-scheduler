package com.keyloop.scheduler.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read only: reference data comes from the seed. */
interface DealershipJpaRepository extends Repository<DealershipEntity, Long> {

  Optional<DealershipEntity> findById(long id);
}
