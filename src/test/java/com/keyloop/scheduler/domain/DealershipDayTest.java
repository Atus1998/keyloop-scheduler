package com.keyloop.scheduler.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import org.junit.jupiter.api.Test;

class DealershipDayTest {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");

  @Test
  void normalDayRunsFromLocalMidnightToNextLocalMidnight() {
    DealershipDay day = DealershipDay.containing(Instant.parse("2030-10-22T08:00:00Z"), LONDON);

    assertThat(day.start()).isEqualTo(Instant.parse("2030-10-21T23:00:00Z"));
    assertThat(day.end()).isEqualTo(Instant.parse("2030-10-22T23:00:00Z"));
  }

  @Test
  void dayWhenClocksGoBackLasts25Hours() {
    DealershipDay day = DealershipDay.containing(Instant.parse("2030-10-27T12:00:00Z"), LONDON);

    assertThat(day.start()).isEqualTo(Instant.parse("2030-10-26T23:00:00Z"));
    assertThat(day.end()).isEqualTo(Instant.parse("2030-10-28T00:00:00Z"));
    assertThat(Duration.between(day.start(), day.end())).isEqualTo(Duration.ofHours(25));
  }

  @Test
  void dayWhenClocksGoForwardLasts23Hours() {
    DealershipDay day = DealershipDay.containing(Instant.parse("2030-03-31T12:00:00Z"), LONDON);

    assertThat(day.start()).isEqualTo(Instant.parse("2030-03-31T00:00:00Z"));
    assertThat(day.end()).isEqualTo(Instant.parse("2030-03-31T23:00:00Z"));
    assertThat(Duration.between(day.start(), day.end())).isEqualTo(Duration.ofHours(23));
  }

  @Test
  void instantAtLocalMidnightStartsItsDay() {
    Instant midnight = Instant.parse("2030-10-21T23:00:00Z");

    DealershipDay day = DealershipDay.containing(midnight, LONDON);

    assertThat(day.start()).isEqualTo(midnight);
  }

  @Test
  void lastNanosecondBeforeNextMidnightBelongsToSameDay() {
    DealershipDay day =
        DealershipDay.containing(Instant.parse("2030-10-22T22:59:59.999999999Z"), LONDON);

    assertThat(day.start()).isEqualTo(Instant.parse("2030-10-21T23:00:00Z"));
    assertThat(day.end()).isEqualTo(Instant.parse("2030-10-22T23:00:00Z"));
  }

  @Test
  void dayStartsAtFirstValidTimeWhenMidnightIsSkipped() {
    ZoneId santiago = ZoneId.of("America/Santiago");
    ZoneOffsetTransition gap =
        santiago.getRules().nextTransition(Instant.parse("2030-08-01T00:00:00Z"));
    assertThat(gap.isGap()).as("Santiago moves clocks forward at midnight").isTrue();
    assertThat(gap.getDateTimeBefore().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);

    DealershipDay day = DealershipDay.containing(gap.getInstant().plusSeconds(3600), santiago);

    assertThat(day.start()).isEqualTo(gap.getInstant());
  }

  @Test
  void rejectsEndThatIsNotAfterStart() {
    Instant instant = Instant.parse("2030-10-22T08:00:00Z");

    assertThatThrownBy(() -> new DealershipDay(instant, instant))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
