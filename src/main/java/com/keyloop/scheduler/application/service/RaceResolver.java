package com.keyloop.scheduler.application.service;

import com.keyloop.scheduler.application.port.in.BookingResult;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.application.service.ResourceConflictException.Kind;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.Reason;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Settles a lost race ({@code docs/system-design.md}, section 6, "After a DB error"). The failed
 * transaction is already rolled back. On a deadlock the same insert runs once more, with the same
 * technician and bay. If that does not store it, a new transaction looks again to tell the client
 * what was taken.
 */
final class RaceResolver {

  private final AvailabilityPort availability;
  private final AppointmentPort appointments;
  private final TransactionPort transactions;

  RaceResolver(
      AvailabilityPort availability, AppointmentPort appointments, TransactionPort transactions) {
    this.availability = Objects.requireNonNull(availability, "availability");
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
  }

  /** Any error other than a resource conflict passes through unchanged. */
  BookingResult resolve(Appointment attempted, ResourceConflictException conflict) {
    if (conflict.kind() == Kind.DEADLOCK) {
      Optional<Appointment> replayed = replay(attempted);
      if (replayed.isPresent()) {
        return BookingResult.created(replayed.get());
      }
    }
    return transactions.inReadOnlyTransaction(() -> lookAgain(attempted));
  }

  /**
   * The replay waits for the winner's transaction, so it ends in a conflict once the winner
   * commits, or stores the row if the winner rolled back. Empty when it lost again.
   */
  private Optional<Appointment> replay(Appointment attempted) {
    try {
      return Optional.of(transactions.inTransaction(() -> appointments.insert(attempted)));
    } catch (ResourceConflictException lostAgain) {
      return Optional.empty();
    }
  }

  private BookingResult lookAgain(Appointment attempted) {
    Slot slot =
        new Slot(
            attempted.dealershipId(),
            attempted.vehicleId(),
            attempted.serviceTypeId(),
            attempted.startAt(),
            attempted.endAt());
    return retryMatch(slot)
        .map(BookingResult::retried)
        .orElseGet(() -> retryOrReject(slot, takenReason(attempted)));
  }

  /**
   * Gives the answer for a check that found something taken. Each check is its own statement and
   * sees the latest commits (READ COMMITTED), so a booking that commits between two checks is seen
   * only by the later one. Reading the retry match and the vehicle again after that check sees it
   * too, and gives the same answer as if the other booking had come first. {@code SLOT_TAKEN} means
   * nothing was seen, so there is nothing to read again.
   *
   * @throws BookingRejectedException unless the retry match now exists
   */
  BookingResult retryOrReject(Slot slot, Reason found) {
    if (found == Reason.SLOT_TAKEN) {
      throw new BookingRejectedException(found);
    }
    Optional<Appointment> retryMatch = retryMatch(slot);
    if (retryMatch.isPresent()) {
      return BookingResult.retried(retryMatch.get());
    }
    if (found != Reason.VEHICLE_BUSY
        && availability.isVehicleBusy(slot.vehicleId(), slot.start(), slot.end())) {
      throw new BookingRejectedException(Reason.VEHICLE_BUSY);
    }
    throw new BookingRejectedException(found);
  }

  private Optional<Appointment> retryMatch(Slot slot) {
    return appointments.findConfirmedRetryMatch(
        slot.dealershipId(), slot.vehicleId(), slot.serviceTypeId(), slot.start());
  }

  /** In the order of the look-again table. */
  private Reason takenReason(Appointment attempted) {
    if (availability.isVehicleBusy(attempted.vehicleId(), attempted.startAt(), attempted.endAt())) {
      return Reason.VEHICLE_BUSY;
    }
    if (availability.isTechnicianBusy(
        attempted.technicianId(), attempted.startAt(), attempted.endAt())) {
      return Reason.TECHNICIAN_TAKEN;
    }
    if (availability.isBayBusy(attempted.bayId(), attempted.startAt(), attempted.endAt())) {
      return Reason.BAY_TAKEN;
    }
    return Reason.SLOT_TAKEN;
  }

  /** What a booking asks for, before a technician and a bay are chosen. */
  record Slot(long dealershipId, long vehicleId, long serviceTypeId, Instant start, Instant end) {}
}
