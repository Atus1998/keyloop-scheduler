package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.domain.Dealership;
import com.keyloop.scheduler.domain.ServiceType;
import com.keyloop.scheduler.domain.Technician;
import com.keyloop.scheduler.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ReferenceDataAdapter implements ReferenceDataPort {

  private final DealershipJpaRepository dealerships;
  private final VehicleJpaRepository vehicles;
  private final ServiceTypeJpaRepository serviceTypes;
  private final TechnicianJpaRepository technicians;

  ReferenceDataAdapter(
      DealershipJpaRepository dealerships,
      VehicleJpaRepository vehicles,
      ServiceTypeJpaRepository serviceTypes,
      TechnicianJpaRepository technicians) {
    this.dealerships = dealerships;
    this.vehicles = vehicles;
    this.serviceTypes = serviceTypes;
    this.technicians = technicians;
  }

  @Override
  public Optional<Dealership> findDealership(long id) {
    return dealerships.findById(id).map(DealershipEntity::toDomain);
  }

  @Override
  public Optional<Vehicle> findVehicle(long id) {
    return vehicles.findById(id).map(VehicleEntity::toDomain);
  }

  @Override
  public Optional<ServiceType> findServiceType(long id) {
    return serviceTypes.findById(id).map(ServiceTypeEntity::toDomain);
  }

  @Override
  public List<Technician> findQualifiedTechnicians(long dealershipId, long serviceTypeId) {
    return technicians.findQualified(dealershipId, serviceTypeId).stream()
        .map(TechnicianEntity::toDomain)
        .toList();
  }
}
