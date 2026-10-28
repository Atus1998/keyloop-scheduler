package com.keyloop.scheduler.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Picks the free technician with the fewest appointments that day; ties go to the lowest id. */
public final class TechnicianPicker {

  private static final Comparator<TechnicianCandidate> FEWEST_LOAD_THEN_LOWEST_ID =
      Comparator.comparingLong(TechnicianCandidate::confirmedAppointmentsThatDay)
          .thenComparingLong(TechnicianCandidate::technicianId);

  private TechnicianPicker() {}

  public static OptionalLong pick(List<TechnicianCandidate> freeQualifiedTechnicians) {
    Objects.requireNonNull(freeQualifiedTechnicians, "freeQualifiedTechnicians");
    return freeQualifiedTechnicians.stream()
        .min(FEWEST_LOAD_THEN_LOWEST_ID)
        .map(candidate -> OptionalLong.of(candidate.technicianId()))
        .orElseGet(OptionalLong::empty);
  }
}
