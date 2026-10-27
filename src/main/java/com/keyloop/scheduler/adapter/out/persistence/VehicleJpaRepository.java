package com.keyloop.scheduler.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface VehicleJpaRepository extends JpaRepository<VehicleEntity, Long> {}
