package com.keyloop.scheduler.adapter.out.persistence;

/** A row of {@link AvailabilityJpaRepository#findFreeQualifiedTechnicians}. */
interface FreeTechnicianRow {

  Long getTechnicianId();

  Long getConfirmedThatDay();
}
