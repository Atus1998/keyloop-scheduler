package com.keyloop.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.port.in.BookingResult;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.application.service.ResourceConflictException.Kind;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.DealershipDay;
import com.keyloop.scheduler.domain.Reason;
import com.keyloop.scheduler.domain.TechnicianCandidate;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A lost race ({@code docs/system-design.md}, section 6, "After a DB error"), made deterministic.
 * Each insert follows a script: a real {@code 23P01} comes from a competing row committed on
 * another thread just before the insert; a {@code 40P01} is thrown as the adapter would translate
 * it.
 */
@PostgresIntegrationTest
class BookAppointmentRaceTest {

  private static final Instant NOW = Instant.parse("2030-10-21T00:00:00Z");

  /** 09:00 to 10:30 London time (BST) on Tuesday 22 October 2030. */
  private static final Instant START = Instant.parse("2030-10-22T08:00:00Z");

  private static final Instant END = Instant.parse("2030-10-22T09:30:00Z");

  @Autowired private ReferenceDataPort referenceData;
  @Autowired private AvailabilityPort availability;
  @Autowired private AppointmentPort appointments;
  @Autowired private TransactionPort transactions;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private ScriptedAppointmentPort scripted;
  private CountingAvailabilityPort counting;
  private long dealershipId;
  private long serviceTypeId;
  private long technicianId;
  private long otherTechnicianId;
  private long bayId;
  private long otherBayId;
  private long vehicleId;
  private long otherVehicleId;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    technicianId = fixtures.insertTechnician(dealershipId, "Picked");
    fixtures.insertSkill(technicianId, serviceTypeId);
    otherTechnicianId = fixtures.insertTechnician(dealershipId, "Other");
    bayId = fixtures.insertBay(dealershipId);
    otherBayId = fixtures.insertBay(dealershipId);
    long customerId = fixtures.insertCustomer();
    vehicleId = fixtures.insertVehicle(customerId, "AB12 CDE");
    otherVehicleId = fixtures.insertVehicle(customerId, "XY34 ZZZ");
    scripted = new ScriptedAppointmentPort(appointments);
    counting = new CountingAvailabilityPort(availability);
  }

  @Test
  void returns200WithCompetitorWhenExclusionCameFromTheSameBooking() {
    scripted.then(commitCompetitorThenInsert(vehicleId, technicianId, bayId, START, END));

    BookingResult result = book();

    long competitorId = onlyStoredId();
    assertThat(result.created()).isFalse();
    assertThat(result.appointment().id()).isEqualTo(competitorId);
  }

  @Test
  void rejectsWithVehicleBusyBeforeTechnicianTakenAfterExclusion() {
    scripted.then(
        commitCompetitorThenInsert(
            vehicleId, technicianId, bayId, START.plusSeconds(1800), END.plusSeconds(1800)));

    assertRejected(Reason.VEHICLE_BUSY);
  }

  @Test
  void rejectsWithTechnicianTakenBeforeBayTakenAfterExclusion() {
    scripted.then(commitCompetitorThenInsert(otherVehicleId, technicianId, bayId, START, END));

    assertRejected(Reason.TECHNICIAN_TAKEN);
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void rejectsWithBayTakenWhenOnlyTheChosenBayIsTaken() {
    scripted.then(commitCompetitorThenInsert(otherVehicleId, otherTechnicianId, bayId, START, END));

    assertRejected(Reason.BAY_TAKEN);
  }

  @Test
  void rejectsWithSlotTakenWhenLookingAgainFindsNothing() {
    scripted.then(throwConflict(Kind.EXCLUSION));

    assertRejected(Reason.SLOT_TAKEN);
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void doesNotReplayTheInsertAfterExclusion() {
    scripted.then(commitCompetitorThenInsert(otherVehicleId, technicianId, bayId, START, END));

    assertRejected(Reason.TECHNICIAN_TAKEN);
    assertThat(scripted.attempts()).hasSize(1);
  }

  @Test
  void replaysTheSameInsertOnceAfterDeadlockAndReturns201() {
    scripted.then(insertThenThrow(Kind.DEADLOCK)).then(scripted::insertForReal);

    BookingResult result = book();

    assertThat(result.created()).isTrue();
    assertThat(result.appointment().id()).isEqualTo(onlyStoredId());
    assertThat(result.appointment().technicianId()).isEqualTo(technicianId);
    assertThat(result.appointment().bayId()).isEqualTo(bayId);
    assertThat(scripted.attempts()).hasSize(2);
    assertThat(scripted.attempts().get(1)).isEqualTo(scripted.attempts().get(0));
    assertThat(counting.technicianSearches()).isEqualTo(1);
  }

  @Test
  void looksAgainWhenTheReplayHitsExclusion() {
    scripted
        .then(throwConflict(Kind.DEADLOCK))
        .then(commitCompetitorThenInsert(otherVehicleId, technicianId, otherBayId, START, END));

    assertRejected(Reason.TECHNICIAN_TAKEN);
    assertThat(scripted.attempts()).hasSize(2);
  }

  @Test
  void looksAgainWhenTheReplayHitsDeadlockAgain() {
    scripted.then(throwConflict(Kind.DEADLOCK)).then(throwConflict(Kind.DEADLOCK));

    assertRejected(Reason.SLOT_TAKEN);
    assertThat(scripted.attempts()).hasSize(2);
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void passesOnOtherErrorFromTheReplay() {
    IllegalStateException failure = new IllegalStateException("Connection lost");
    scripted
        .then(throwConflict(Kind.DEADLOCK))
        .then(
            attempted -> {
              throw failure;
            });

    assertThatThrownBy(this::book).isSameAs(failure);
  }

  @Test
  void passesOnOtherErrorFromLookingAgain() {
    IllegalStateException failure = new IllegalStateException("Connection lost");
    counting.failTechnicianCheckWith(failure);
    scripted.then(throwConflict(Kind.EXCLUSION));

    assertThatThrownBy(this::book).isSameAs(failure);
  }

  private BookingResult book() {
    return new BookAppointmentService(
            referenceData, counting, scripted, transactions, Clock.fixed(NOW, ZoneOffset.UTC))
        .book(new BookAppointmentCommand(dealershipId, vehicleId, serviceTypeId, START));
  }

  private void assertRejected(Reason reason) {
    assertThatThrownBy(this::book)
        .isInstanceOfSatisfying(
            BookingRejectedException.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }

  private long onlyStoredId() {
    return jdbc.queryForObject("SELECT id FROM appointment", Long.class);
  }

  /**
   * Commits a competing row on another thread, so it is not part of the open transaction, then runs
   * the real insert, which now fails with {@code 23P01}.
   */
  private UnaryOperator<Appointment> commitCompetitorThenInsert(
      long vehicle, long technician, long bay, Instant start, Instant end) {
    return attempted -> {
      CompletableFuture.runAsync(
              () ->
                  fixtures.insertAppointment(
                      dealershipId,
                      vehicle,
                      serviceTypeId,
                      technician,
                      bay,
                      start,
                      end,
                      "CONFIRMED"))
          .join();
      return scripted.insertForReal(attempted);
    };
  }

  /** Stores the row, then fails: the transaction must roll the row back. */
  private UnaryOperator<Appointment> insertThenThrow(Kind kind) {
    return attempted -> {
      scripted.insertForReal(attempted);
      throw conflict(kind);
    };
  }

  private static UnaryOperator<Appointment> throwConflict(Kind kind) {
    return attempted -> {
      throw conflict(kind);
    };
  }

  private static ResourceConflictException conflict(Kind kind) {
    String state = kind == Kind.DEADLOCK ? "40P01" : "23P01";
    return new ResourceConflictException(kind, new SQLException("Scripted", state));
  }

  /** Records each insert and runs the next scripted step; with no step left, inserts for real. */
  private static final class ScriptedAppointmentPort implements AppointmentPort {

    private final AppointmentPort delegate;
    private final Deque<UnaryOperator<Appointment>> steps = new ArrayDeque<>();
    private final List<Appointment> attempts = new ArrayList<>();

    ScriptedAppointmentPort(AppointmentPort delegate) {
      this.delegate = delegate;
    }

    ScriptedAppointmentPort then(UnaryOperator<Appointment> step) {
      steps.addLast(step);
      return this;
    }

    List<Appointment> attempts() {
      return List.copyOf(attempts);
    }

    Appointment insertForReal(Appointment appointment) {
      return delegate.insert(appointment);
    }

    @Override
    public Appointment insert(Appointment appointment) {
      attempts.add(appointment);
      UnaryOperator<Appointment> step = steps.pollFirst();
      return step == null ? delegate.insert(appointment) : step.apply(appointment);
    }

    @Override
    public Optional<Appointment> findById(long id) {
      return delegate.findById(id);
    }

    @Override
    public Optional<Appointment> findConfirmedRetryMatch(
        long dealershipId, long vehicleId, long serviceTypeId, Instant start) {
      return delegate.findConfirmedRetryMatch(dealershipId, vehicleId, serviceTypeId, start);
    }
  }

  /** Counts technician searches and can fail the technician check of the look-again step. */
  private static final class CountingAvailabilityPort implements AvailabilityPort {

    private final AvailabilityPort delegate;
    private final AtomicInteger technicianSearches = new AtomicInteger();
    private RuntimeException technicianCheckFailure;

    CountingAvailabilityPort(AvailabilityPort delegate) {
      this.delegate = delegate;
    }

    int technicianSearches() {
      return technicianSearches.get();
    }

    void failTechnicianCheckWith(RuntimeException failure) {
      technicianCheckFailure = failure;
    }

    @Override
    public boolean isVehicleBusy(long vehicleId, Instant start, Instant end) {
      return delegate.isVehicleBusy(vehicleId, start, end);
    }

    @Override
    public boolean isTechnicianBusy(long technicianId, Instant start, Instant end) {
      if (technicianCheckFailure != null) {
        throw technicianCheckFailure;
      }
      return delegate.isTechnicianBusy(technicianId, start, end);
    }

    @Override
    public boolean isBayBusy(long bayId, Instant start, Instant end) {
      return delegate.isBayBusy(bayId, start, end);
    }

    @Override
    public List<TechnicianCandidate> findFreeQualifiedTechnicians(
        long dealershipId, long serviceTypeId, Instant start, Instant end, DealershipDay day) {
      technicianSearches.incrementAndGet();
      return delegate.findFreeQualifiedTechnicians(dealershipId, serviceTypeId, start, end, day);
    }

    @Override
    public List<Long> findFreeBayIds(long dealershipId, Instant start, Instant end) {
      return delegate.findFreeBayIds(dealershipId, start, end);
    }
  }
}
