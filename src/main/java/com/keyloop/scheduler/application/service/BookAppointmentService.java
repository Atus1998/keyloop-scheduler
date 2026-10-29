package com.keyloop.scheduler.application.service;

import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.application.port.in.BookingResult;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.domain.BayPicker;
import com.keyloop.scheduler.domain.BookingRules;
import com.keyloop.scheduler.domain.Dealership;
import com.keyloop.scheduler.domain.DealershipDay;
import com.keyloop.scheduler.domain.Reason;
import com.keyloop.scheduler.domain.ServiceType;
import com.keyloop.scheduler.domain.Technician;
import com.keyloop.scheduler.domain.TechnicianPicker;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Books an appointment ({@code docs/system-design.md}, section 6). Reference data and the retry
 * match are read in a read-only transaction. A retry match returns the stored appointment before
 * any booking rule runs. The availability check and the insert share one read-write transaction.
 */
public final class BookAppointmentService implements BookAppointmentUseCase {

  private final ReferenceDataPort referenceData;
  private final AvailabilityPort availability;
  private final AppointmentPort appointments;
  private final TransactionPort transactions;
  private final Clock clock;

  public BookAppointmentService(
      ReferenceDataPort referenceData,
      AvailabilityPort availability,
      AppointmentPort appointments,
      TransactionPort transactions,
      Clock clock) {
    this.referenceData = Objects.requireNonNull(referenceData, "referenceData");
    this.availability = Objects.requireNonNull(availability, "availability");
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public BookingResult book(BookAppointmentCommand command) {
    Objects.requireNonNull(command, "command");
    return switch (transactions.inReadOnlyTransaction(() -> load(command))) {
      case Retry retry -> BookingResult.retried(retry.existing());
      case NewBooking newBooking -> BookingResult.created(bookNew(command, newBooking.context()));
    };
  }

  private Appointment bookNew(BookAppointmentCommand command, BookingContext context) {
    rejectIfPresent(
        BookingRules.check(
            context.dealership(),
            context.serviceType(),
            context.qualifiedTechnicians(),
            command.startTime(),
            clock.instant()));
    // Safe only after the check: it rejects starts near Instant.MAX.
    Instant end = command.startTime().plus(context.serviceType().duration());
    return transactions.inTransaction(() -> reserve(command, context.dealership(), end));
  }

  /**
   * Checks the ids in the order of the booking checks table, then looks for a retry match. The
   * qualified technicians are read only when there is none.
   */
  private Loaded load(BookAppointmentCommand command) {
    Dealership dealership =
        referenceData
            .findDealership(command.dealershipId())
            .orElseThrow(() -> notFound(Reason.DEALERSHIP_NOT_FOUND, command.dealershipId()));
    referenceData
        .findVehicle(command.vehicleId())
        .orElseThrow(() -> notFound(Reason.VEHICLE_NOT_FOUND, command.vehicleId()));
    ServiceType serviceType =
        referenceData
            .findServiceType(command.serviceTypeId())
            .orElseThrow(() -> notFound(Reason.SERVICE_TYPE_NOT_FOUND, command.serviceTypeId()));
    Optional<Appointment> retryMatch =
        appointments.findConfirmedRetryMatch(
            dealership.id(), command.vehicleId(), serviceType.id(), command.startTime());
    if (retryMatch.isPresent()) {
      return new Retry(retryMatch.get());
    }
    List<Technician> qualified =
        referenceData.findQualifiedTechnicians(dealership.id(), serviceType.id());
    return new NewBooking(new BookingContext(dealership, serviceType, qualified));
  }

  private Appointment reserve(BookAppointmentCommand command, Dealership dealership, Instant end) {
    Instant start = command.startTime();
    if (availability.isVehicleBusy(command.vehicleId(), start, end)) {
      throw new BookingRejectedException(Reason.VEHICLE_BUSY);
    }
    DealershipDay day = DealershipDay.containing(start, dealership.zoneId());
    OptionalLong technicianId =
        TechnicianPicker.pick(
            availability.findFreeQualifiedTechnicians(
                dealership.id(), command.serviceTypeId(), start, end, day));
    OptionalLong bayId = BayPicker.pick(availability.findFreeBayIds(dealership.id(), start, end));
    rejectIfPresent(BookingRules.availability(technicianId, bayId));
    return appointments.insert(
        new Appointment(
            null,
            dealership.id(),
            command.vehicleId(),
            command.serviceTypeId(),
            technicianId.getAsLong(),
            bayId.getAsLong(),
            start,
            end,
            AppointmentStatus.CONFIRMED));
  }

  private static void rejectIfPresent(Optional<Reason> reason) {
    reason.ifPresent(
        rejected -> {
          throw new BookingRejectedException(rejected);
        });
  }

  private static BookingRejectedException notFound(Reason reason, long id) {
    return new BookingRejectedException(reason, id);
  }

  /** What the read-only step found: a retry match, or what a new booking needs. */
  private sealed interface Loaded permits Retry, NewBooking {}

  private record Retry(Appointment existing) implements Loaded {}

  private record NewBooking(BookingContext context) implements Loaded {}

  private record BookingContext(
      Dealership dealership, ServiceType serviceType, List<Technician> qualifiedTechnicians) {}
}
