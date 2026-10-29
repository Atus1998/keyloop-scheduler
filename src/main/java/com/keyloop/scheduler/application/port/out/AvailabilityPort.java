package com.keyloop.scheduler.application.port.out;

import com.keyloop.scheduler.domain.DealershipDay;
import com.keyloop.scheduler.domain.TechnicianCandidate;
import java.time.Instant;
import java.util.List;

/**
 * Finds what is free in {@code [start, end)}. Only CONFIRMED appointments block; back-to-back
 * appointments do not overlap.
 */
public interface AvailabilityPort {

  /** True when the vehicle has an overlapping appointment at any dealership. */
  boolean isVehicleBusy(long vehicleId, Instant start, Instant end);

  /** True when the technician has an overlapping appointment. */
  boolean isTechnicianBusy(long technicianId, Instant start, Instant end);

  /** True when the bay has an overlapping appointment. */
  boolean isBayBusy(long bayId, Instant start, Instant end);

  /**
   * Free technicians of the dealership who have the skill, ordered by id, each with the number of
   * CONFIRMED appointments that start within {@code day}.
   */
  List<TechnicianCandidate> findFreeQualifiedTechnicians(
      long dealershipId, long serviceTypeId, Instant start, Instant end, DealershipDay day);

  /** Free bays of the dealership, ordered by id. */
  List<Long> findFreeBayIds(long dealershipId, Instant start, Instant end);
}
