package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.domain.DealershipDay;
import com.keyloop.scheduler.domain.TechnicianCandidate;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class AvailabilityAdapter implements AvailabilityPort {

  @Override
  public boolean isVehicleBusy(long vehicleId, Instant start, Instant end) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public List<TechnicianCandidate> findFreeQualifiedTechnicians(
      long dealershipId, long serviceTypeId, Instant start, Instant end, DealershipDay day) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public List<Long> findFreeBayIds(long dealershipId, Instant start, Instant end) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
