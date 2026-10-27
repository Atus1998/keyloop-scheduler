package com.keyloop.scheduler.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, Long> {}
