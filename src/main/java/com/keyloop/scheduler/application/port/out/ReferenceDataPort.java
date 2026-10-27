package com.keyloop.scheduler.application.port.out;

import com.keyloop.scheduler.domain.Dealership;
import com.keyloop.scheduler.domain.ServiceType;
import com.keyloop.scheduler.domain.Technician;
import com.keyloop.scheduler.domain.Vehicle;
import java.util.List;
import java.util.Optional;

/** Reads seed data. Unknown ids give an empty result, never an exception. */
public interface ReferenceDataPort {

  Optional<Dealership> findDealership(long id);

  Optional<Vehicle> findVehicle(long id);

  Optional<ServiceType> findServiceType(long id);

  /** Technicians of the dealership who have the skill, ordered by id. */
  List<Technician> findQualifiedTechnicians(long dealershipId, long serviceTypeId);
}
