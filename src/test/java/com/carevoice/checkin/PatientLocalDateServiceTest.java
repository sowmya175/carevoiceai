package com.carevoice.checkin;

import com.carevoice.domain.Patient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PatientLocalDateServiceTest {
    private static final Instant LATE_EVENING = Instant.parse("2026-10-09T02:30:00Z");

    @Test
    void newYorkEveningIsStillThePreviousLocalDate() {
        assertThat(today("America/New_York", LATE_EVENING)).isEqualTo(LocalDate.of(2026, 10, 8));
        Patient patient = new Patient("Ada", "Recovery", "America/New_York");
        assertThat(new PatientLocalDateService(Clock.fixed(LATE_EVENING, ZoneOffset.UTC)).localTime(patient))
                .isEqualTo(LocalTime.of(22, 30));
    }

    @Test
    void utcUsesTheInstantDate() {
        assertThat(today("UTC", LATE_EVENING)).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void losAngelesUsesItsOwnDate() {
        assertThat(today("America/Los_Angeles", LATE_EVENING)).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void daylightSavingUsesTheZoneRulesInsteadOfAFixedOffset() {
        Instant afterSpringForward = Instant.parse("2026-03-08T07:30:00Z");
        Patient patient = new Patient("Ada", "Recovery", "America/New_York");
        Clock clock = Clock.fixed(afterSpringForward, ZoneOffset.UTC);

        assertThat(new PatientLocalDateService(clock).today(patient)).isEqualTo(LocalDate.of(2026, 3, 8));
        ZonedDateTime local = ZonedDateTime.ofInstant(afterSpringForward, ZoneId.of("America/New_York"));
        assertThat(local.toLocalTime().getHour()).isEqualTo(3);
        assertThat(local.getOffset()).isEqualTo(ZoneOffset.ofHours(-4));
    }

    @Test
    void aMissingTimezoneUsesUtc() {
        Patient patient = new Patient("Legacy", "Daily monitoring");
        Clock clock = Clock.fixed(LATE_EVENING, ZoneOffset.UTC);

        assertThat(new PatientLocalDateService(clock).today(patient)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(new PatientLocalDateService(clock).zone(patient)).isEqualTo(ZoneOffset.UTC);
    }

    private static LocalDate today(String timezone, Instant instant) {
        Patient patient = new Patient("Ada", "Recovery", timezone);
        return new PatientLocalDateService(Clock.fixed(instant, ZoneOffset.UTC)).today(patient);
    }
}
