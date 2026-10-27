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

  @Override
  public Optional<Dealership> findDealership(long id) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Optional<Vehicle> findVehicle(long id) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Optional<ServiceType> findServiceType(long id) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public List<Technician> findQualifiedTechnicians(long dealershipId, long serviceTypeId) {
    throw new UnsupportedOperationException("not implemented");
  }
}
