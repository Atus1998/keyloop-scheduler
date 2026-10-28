package com.keyloop.scheduler.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TechnicianPickerTest {

  private static TechnicianCandidate candidate(long id, long load) {
    return new TechnicianCandidate(id, load);
  }

  @Test
  void picksTechnicianWithFewestAppointmentsThatDay() {
    var candidates = List.of(candidate(1, 3), candidate(2, 1), candidate(3, 2));

    assertThat(TechnicianPicker.pick(candidates)).hasValue(2);
  }

  @Test
  void picksLowestIdWhenLoadsTie() {
    var candidates = List.of(candidate(5, 1), candidate(3, 1), candidate(4, 2));

    assertThat(TechnicianPicker.pick(candidates)).hasValue(3);
  }

  @Test
  void lowerLoadBeatsLowerId() {
    var candidates = List.of(candidate(1, 2), candidate(9, 0));

    assertThat(TechnicianPicker.pick(candidates)).hasValue(9);
  }

  @Test
  void picksTheOnlyCandidate() {
    assertThat(TechnicianPicker.pick(List.of(candidate(4, 7)))).hasValue(4);
  }

  @Test
  void returnsEmptyWhenNoTechnicianIsFree() {
    assertThat(TechnicianPicker.pick(List.of())).isEmpty();
  }

  @Test
  void rejectsNegativeAppointmentCount() {
    assertThatIllegalArgumentException().isThrownBy(() -> candidate(1, -1));
  }

  @Test
  void rejectsNullListByName() {
    assertThatNullPointerException()
        .isThrownBy(() -> TechnicianPicker.pick(null))
        .withMessage("freeQualifiedTechnicians");
  }

  @Test
  void rejectsNullCandidateInList() {
    var candidates = Arrays.asList(candidate(1, 0), null);

    assertThatNullPointerException()
        .isThrownBy(() -> TechnicianPicker.pick(candidates))
        .withMessage("freeQualifiedTechnicians contains null");
  }
}
