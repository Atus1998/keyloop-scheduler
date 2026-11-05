package com.keyloop.scheduler.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.keyloop.scheduler.PostgresIntegrationTest;
import com.keyloop.scheduler.application.port.in.BookAppointmentCommand;
import com.keyloop.scheduler.application.port.in.BookingResult;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.application.port.out.DealershipDayLockPort;
import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.domain.AppointmentStatus;
import com.keyloop.scheduler.domain.Dealership;
import com.keyloop.scheduler.domain.DealershipDay;
import com.keyloop.scheduler.domain.Reason;
import com.keyloop.scheduler.domain.ServiceType;
import com.keyloop.scheduler.domain.Technician;
import com.keyloop.scheduler.domain.TechnicianCandidate;
import com.keyloop.scheduler.domain.Vehicle;
import com.keyloop.scheduler.testsupport.PersistenceFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The booking flow on a real PostgreSQL. Each test builds the service with its own clock. */
@PostgresIntegrationTest
class BookAppointmentServiceTest {

  private static final Instant NOW = Instant.parse("2030-10-21T00:00:00Z");

  /** 09:00 London time (BST) on Tuesday 22 October 2030. */
  private static final Instant START = Instant.parse("2030-10-22T08:00:00Z");

  @Autowired private ReferenceDataPort referenceData;
  @Autowired private AvailabilityPort availability;
  @Autowired private AppointmentPort appointments;
  @Autowired private TransactionPort transactions;
  @Autowired private DealershipDayLockPort locks;
  @Autowired private JdbcTemplate jdbc;

  private PersistenceFixtures fixtures;
  private long dealershipId;
  private long serviceTypeId;
  private long bayId;
  private long vehicleId;
  private long otherVehicleId;

  @BeforeEach
  void setUp() {
    fixtures = new PersistenceFixtures(jdbc);
    fixtures.truncateAll();
    dealershipId = fixtures.insertDealership("Europe/London", "09:00", "18:00");
    serviceTypeId = fixtures.insertServiceType("Oil change", 90);
    bayId = fixtures.insertBay(dealershipId);
    long customerId = fixtures.insertCustomer();
    vehicleId = fixtures.insertVehicle(customerId, "AB12 CDE");
    otherVehicleId = fixtures.insertVehicle(customerId, "XY34 ZZZ");
  }

  @Test
  void storesConfirmedAppointmentWithEndFromServiceDuration() {
    long technicianId = skilledTechnician("Only");

    Appointment booked = service(NOW).book(command(START)).appointment();

    assertThat(booked.id()).isNotNull();
    assertThat(booked)
        .isEqualTo(
            new Appointment(
                booked.id(),
                dealershipId,
                vehicleId,
                serviceTypeId,
                technicianId,
                bayId,
                START,
                Instant.parse("2030-10-22T09:30:00Z"),
                AppointmentStatus.CONFIRMED));
    assertThat(appointments.findById(booked.id())).contains(booked);
  }

  @Test
  void picksTechnicianWithFewestConfirmedAppointmentsThatDay() {
    long busier = skilledTechnician("Busier");
    long lessBusy = skilledTechnician("Less busy");
    bookOther(busier, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).appointment().technicianId()).isEqualTo(lessBusy);
  }

  @Test
  void breaksLoadTieByLowestTechnicianId() {
    long first = skilledTechnician("First");
    long second = skilledTechnician("Second");
    bookOther(first, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");
    bookOther(second, "2030-10-22T14:00:00Z", "2030-10-22T15:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).appointment().technicianId()).isEqualTo(first);
  }

  @Test
  void skipsBusyTechnicianEvenWhenLeastLoaded() {
    long busyNow = skilledTechnician("Busy now");
    long free = skilledTechnician("Free");
    bookOther(busyNow, "2030-10-22T08:30:00Z", "2030-10-22T09:00:00Z", "CONFIRMED");
    bookOther(free, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");
    bookOther(free, "2030-10-22T14:00:00Z", "2030-10-22T15:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).appointment().technicianId()).isEqualTo(free);
  }

  @Test
  void skipsTechnicianWithoutTheSkill() {
    fixtures.insertTechnician(dealershipId, "Unskilled");
    long skilled = skilledTechnician("Skilled");

    assertThat(service(NOW).book(command(START)).appointment().technicianId()).isEqualTo(skilled);
  }

  @Test
  void ignoresCancelledAppointmentsInDayLoad() {
    long first = skilledTechnician("First");
    long second = skilledTechnician("Second");
    bookOther(first, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CANCELLED");
    bookOther(first, "2030-10-22T14:00:00Z", "2030-10-22T15:00:00Z", "CANCELLED");
    bookOther(second, "2030-10-22T12:00:00Z", "2030-10-22T13:00:00Z", "CONFIRMED");

    assertThat(service(NOW).book(command(START)).appointment().technicianId()).isEqualTo(first);
  }

  @Test
  void countsWholeLocalDayWhenClocksGoBack() {
    // 27 October 2030 in London lasts 25 hours: 2030-10-26T23:00Z to 2030-10-28T00:00Z.
    long first = skilledTechnician("First");
    long second = skilledTechnician("Second");
    bookOther(first, "2030-10-27T12:00:00Z", "2030-10-27T13:00:00Z", "CONFIRMED");
    bookOther(first, "2030-10-27T23:30:00Z", "2030-10-27T23:45:00Z", "CONFIRMED");
    bookOther(second, "2030-10-27T14:00:00Z", "2030-10-27T15:00:00Z", "CONFIRMED");

    Appointment booked =
        service(NOW).book(command(Instant.parse("2030-10-27T10:00:00Z"))).appointment();

    assertThat(booked.technicianId()).isEqualTo(second);
  }

  @Test
  void doesNotCountNextDayWhenClocksGoForward() {
    // 31 March 2030 in London lasts 23 hours: 2030-03-31T00:00Z to 2030-03-31T23:00Z.
    long first = skilledTechnician("First");
    skilledTechnician("Second");
    bookOther(first, "2030-03-31T23:30:00Z", "2030-03-31T23:45:00Z", "CONFIRMED");

    Appointment booked =
        service(Instant.parse("2030-03-25T00:00:00Z"))
            .book(command(Instant.parse("2030-03-31T09:00:00Z")))
            .appointment();

    assertThat(booked.technicianId()).isEqualTo(first);
  }

  @Test
  void picksLowestFreeBayId() {
    skilledTechnician("Technician");
    long otherTechnician = fixtures.insertTechnician(dealershipId, "Other");
    long secondBay = fixtures.insertBay(dealershipId);
    fixtures.insertBay(dealershipId);
    fixtures.insertAppointment(
        dealershipId,
        otherVehicleId,
        serviceTypeId,
        otherTechnician,
        bayId,
        START,
        Instant.parse("2030-10-22T09:00:00Z"),
        "CONFIRMED");

    assertThat(service(NOW).book(command(START)).appointment().bayId()).isEqualTo(secondBay);
  }

  @Test
  void readsNowFromTheClock() {
    skilledTechnician("Technician");

    assertThatThrownBy(() -> service(START).book(command(START)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.START_IN_PAST));
    assertThat(fixtures.countAppointments()).isZero();
  }

  @Test
  void reportsFirstFailingRuleInTableOrder() {
    skilledTechnician("Technician");
    Instant pastAndAfterClosing = Instant.parse("2030-10-20T20:00:00Z");

    assertThatThrownBy(() -> service(NOW).book(command(pastAndAfterClosing)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.START_IN_PAST));
  }

  @Test
  void rejectsWithNoBayAndStoresNothingWhenTheOnlyBayIsTaken() {
    skilledTechnician("Technician");
    long otherTechnician = fixtures.insertTechnician(dealershipId, "Other");
    fixtures.insertAppointment(
        dealershipId,
        otherVehicleId,
        serviceTypeId,
        otherTechnician,
        bayId,
        START,
        Instant.parse("2030-10-22T09:00:00Z"),
        "CONFIRMED");

    assertThatThrownBy(() -> service(NOW).book(command(START)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NO_BAY));
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void rejectsWithVehicleBusyAndStoresNothing() {
    long technicianId = skilledTechnician("Technician");
    long secondBay = fixtures.insertBay(dealershipId);
    fixtures.insertAppointment(
        dealershipId,
        vehicleId,
        serviceTypeId,
        technicianId,
        secondBay,
        Instant.parse("2030-10-22T07:00:00Z"),
        Instant.parse("2030-10-22T08:30:00Z"),
        "CONFIRMED");

    assertThatThrownBy(() -> service(NOW).book(command(START)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.VEHICLE_BUSY));
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void rejectsUnknownDealershipWithItsId() {
    var command = new BookAppointmentCommand(999_999, vehicleId, serviceTypeId, START);

    assertThatThrownBy(() -> service(NOW).book(command))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> {
              assertThat(e.reason()).isEqualTo(Reason.DEALERSHIP_NOT_FOUND);
              assertThat(e.id()).hasValue(999_999);
            });
  }

  @Test
  void returnsExistingAppointmentAsRetryWhenSameBookingIsConfirmed() {
    skilledTechnician("Technician");
    BookingResult first = service(NOW).book(command(START));

    BookingResult second = service(NOW).book(command(START));

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    assertThat(second.appointment()).isEqualTo(first.appointment());
  }

  @Test
  void addsNoRowOnRetry() {
    skilledTechnician("Technician");
    service(NOW).book(command(START));

    service(NOW).book(command(START));

    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void returnsRetryEvenWhenStartHasPassedSinceFirstBooking() {
    skilledTechnician("Technician");
    Appointment first = service(NOW).book(command(START)).appointment();

    BookingResult second = service(START.plusSeconds(3600)).book(command(START));

    assertThat(second.created()).isFalse();
    assertThat(second.appointment()).isEqualTo(first);
  }

  @Test
  void returnsRetryEvenWhenServiceIsNoLongerOffered() {
    skilledTechnician("Technician");
    Appointment first = service(NOW).book(command(START)).appointment();
    jdbc.update("DELETE FROM technician_skill");

    BookingResult second = service(NOW).book(command(START));

    assertThat(second.created()).isFalse();
    assertThat(second.appointment()).isEqualTo(first);
  }

  @Test
  void booksNewAppointmentWhenMatchingOneIsCancelled() {
    long technicianId = skilledTechnician("Technician");
    long cancelledId =
        fixtures.insertAppointment(
            dealershipId,
            vehicleId,
            serviceTypeId,
            technicianId,
            bayId,
            START,
            Instant.parse("2030-10-22T09:30:00Z"),
            "CANCELLED");

    BookingResult result = service(NOW).book(command(START));

    assertThat(result.created()).isTrue();
    assertThat(result.appointment().id()).isNotEqualTo(cancelledId);
    assertThat(fixtures.countAppointments()).isEqualTo(2);
  }

  @Test
  void rejectsWithVehicleBusyWhenSameStartButOtherServiceType() {
    long technicianId = skilledTechnician("Technician");
    long tyres = fixtures.insertServiceType("Tyres", 60);
    fixtures.insertSkill(technicianId, tyres);
    fixtures.insertBay(dealershipId);
    service(NOW).book(command(START));

    assertThatThrownBy(
            () ->
                service(NOW)
                    .book(new BookAppointmentCommand(dealershipId, vehicleId, tyres, START)))
        .isInstanceOfSatisfying(
            BookingRejectedException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.VEHICLE_BUSY));
    assertThat(fixtures.countAppointments()).isEqualTo(1);
  }

  @Test
  void doesNotReadQualifiedTechniciansOnRetry() {
    skilledTechnician("Technician");
    service(NOW).book(command(START));
    var counting = new CountingReferenceData(referenceData);

    BookingResult retry =
        new BookAppointmentService(
                counting,
                availability,
                appointments,
                transactions,
                locks,
                Clock.fixed(NOW, ZoneOffset.UTC))
            .book(command(START));

    assertThat(retry.created()).isFalse();
    assertThat(counting.qualifiedTechnicianReads).isZero();
  }

  @ParameterizedTest
  @CsvSource({
    // 09:00 BST on Tuesday 22 October 2030.
    "Europe/London, 2030-10-22T08:00:00Z, 2030-10-22",
    // 09:00 NZDT on Wednesday 23 October 2030, still the 22nd in UTC.
    "Pacific/Auckland, 2030-10-22T20:00:00Z, 2030-10-23"
  })
  void locksDealershipAndLocalDayOnceBeforeReadingAvailability(
      String zone, String start, String localDay) {
    long dealership = fixtures.insertDealership(zone, "09:00", "18:00");
    long technicianId = fixtures.insertTechnician(dealership, "Only");
    fixtures.insertSkill(technicianId, serviceTypeId);
    fixtures.insertBay(dealership);
    List<String> events = new ArrayList<>();

    BookingResult result =
        recordingService(events)
            .book(
                new BookAppointmentCommand(
                    dealership, vehicleId, serviceTypeId, Instant.parse(start)));

    assertThat(result.created()).isTrue();
    assertThat(events).as("events").first().isEqualTo("lock " + dealership + " " + localDay);
    assertThat(events).as("events").filteredOn(e -> e.startsWith("lock")).hasSize(1);
    assertThat(events).as("events").contains("isVehicleBusy");
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown dealership", "start in past", "retry"})
  void doesNotLockWhenTheAnswerComesBeforeReserving(String outcome) {
    skilledTechnician("Only");
    if (outcome.equals("retry")) {
      service(NOW).book(command(START));
    }
    BookAppointmentCommand request =
        switch (outcome) {
          case "unknown dealership" ->
              new BookAppointmentCommand(dealershipId + 999, vehicleId, serviceTypeId, START);
          case "start in past" -> command(Instant.parse("2030-10-20T09:00:00Z"));
          default -> command(START);
        };
    List<String> events = new ArrayList<>();

    Throwable thrown = catchThrowable(() -> recordingService(events).book(request));

    if (outcome.equals("retry")) {
      assertThat(thrown).isNull();
    } else {
      assertThat(thrown).isInstanceOf(BookingRejectedException.class);
    }
    assertThat(events).as("events").noneMatch(e -> e.startsWith("lock"));
  }

  private BookAppointmentService service(Instant now) {
    return new BookAppointmentService(
        referenceData,
        availability,
        appointments,
        transactions,
        locks,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  /** Like {@link #service}, but records each lock and each availability read in {@code events}. */
  private BookAppointmentService recordingService(List<String> events) {
    return new BookAppointmentService(
        referenceData,
        new RecordingAvailability(availability, events),
        appointments,
        transactions,
        new RecordingLock(locks, events),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private BookAppointmentCommand command(Instant start) {
    return new BookAppointmentCommand(dealershipId, vehicleId, serviceTypeId, start);
  }

  private long skilledTechnician(String name) {
    long technicianId = fixtures.insertTechnician(dealershipId, name);
    fixtures.insertSkill(technicianId, serviceTypeId);
    return technicianId;
  }

  /**
   * Books another vehicle with this technician, on a bay of its own. The booking under test then
   * still gets the first bay.
   */
  private void bookOther(long technicianId, String start, String end, String status) {
    long ownBay = fixtures.insertBay(dealershipId);
    fixtures.insertAppointment(
        dealershipId,
        otherVehicleId,
        serviceTypeId,
        technicianId,
        ownBay,
        Instant.parse(start),
        Instant.parse(end),
        status);
  }

  /** Delegates to the real port and counts qualified-technician reads. */
  private static final class CountingReferenceData implements ReferenceDataPort {

    private final ReferenceDataPort delegate;
    int qualifiedTechnicianReads;

    CountingReferenceData(ReferenceDataPort delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<Dealership> findDealership(long id) {
      return delegate.findDealership(id);
    }

    @Override
    public Optional<Vehicle> findVehicle(long id) {
      return delegate.findVehicle(id);
    }

    @Override
    public Optional<ServiceType> findServiceType(long id) {
      return delegate.findServiceType(id);
    }

    @Override
    public List<Technician> findQualifiedTechnicians(long dealershipId, long serviceTypeId) {
      qualifiedTechnicianReads++;
      return delegate.findQualifiedTechnicians(dealershipId, serviceTypeId);
    }
  }

  /** Delegates to the real lock and records each call as {@code "lock <dealership> <day>"}. */
  private record RecordingLock(DealershipDayLockPort delegate, List<String> events)
      implements DealershipDayLockPort {

    @Override
    public void lock(long dealershipId, LocalDate day) {
      events.add("lock " + dealershipId + " " + day);
      delegate.lock(dealershipId, day);
    }
  }

  /** Delegates to the real port and records the name of each read. */
  private record RecordingAvailability(AvailabilityPort delegate, List<String> events)
      implements AvailabilityPort {

    @Override
    public boolean isVehicleBusy(long vehicleId, Instant start, Instant end) {
      events.add("isVehicleBusy");
      return delegate.isVehicleBusy(vehicleId, start, end);
    }

    @Override
    public boolean isTechnicianBusy(long technicianId, Instant start, Instant end) {
      events.add("isTechnicianBusy");
      return delegate.isTechnicianBusy(technicianId, start, end);
    }

    @Override
    public boolean isBayBusy(long bayId, Instant start, Instant end) {
      events.add("isBayBusy");
      return delegate.isBayBusy(bayId, start, end);
    }

    @Override
    public List<TechnicianCandidate> findFreeQualifiedTechnicians(
        long dealershipId, long serviceTypeId, Instant start, Instant end, DealershipDay day) {
      events.add("findFreeQualifiedTechnicians");
      return delegate.findFreeQualifiedTechnicians(dealershipId, serviceTypeId, start, end, day);
    }

    @Override
    public List<Long> findFreeBayIds(long dealershipId, Instant start, Instant end) {
      events.add("findFreeBayIds");
      return delegate.findFreeBayIds(dealershipId, start, end);
    }
  }
}
