package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.domain.DealershipDay;
import com.keyloop.scheduler.domain.TechnicianCandidate;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class AvailabilityAdapter implements AvailabilityPort {

  private final AvailabilityJpaRepository repository;

  AvailabilityAdapter(AvailabilityJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean isVehicleBusy(long vehicleId, Instant start, Instant end) {
    return repository.isVehicleBusy(vehicleId, start, end);
  }

  @Override
  public List<TechnicianCandidate> findFreeQualifiedTechnicians(
      long dealershipId, long serviceTypeId, Instant start, Instant end, DealershipDay day) {
    return repository
        .findFreeQualifiedTechnicians(
            dealershipId, serviceTypeId, start, end, day.start(), day.end())
        .stream()
        .map(row -> new TechnicianCandidate(row.getTechnicianId(), row.getConfirmedThatDay()))
        .toList();
  }

  @Override
  public List<Long> findFreeBayIds(long dealershipId, Instant start, Instant end) {
    return repository.findFreeBayIds(dealershipId, start, end);
  }
}
