package com.keyloop.scheduler.application.service;

import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.domain.Appointment;
import java.time.Clock;
import java.util.Objects;

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
  public Appointment book(BookAppointmentCommand command) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
