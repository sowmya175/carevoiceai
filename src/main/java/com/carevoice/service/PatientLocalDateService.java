package com.carevoice.service;

import com.carevoice.domain.Patient;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Converts the current instant into a patient's local calendar date.
 * Patient.timezone is authoritative. An unknown timezone uses UTC, never the server's zone.
 */
@Service
public class PatientLocalDateService {
    private final Clock clock;

    public PatientLocalDateService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today(Patient patient) {
        return zonedNow(patient).toLocalDate();
    }

    public LocalTime localTime(Patient patient) {
        return zonedNow(patient).toLocalTime();
    }

    public ZonedDateTime zonedNow(Patient patient) {
        return ZonedDateTime.ofInstant(clock.instant(), zone(patient));
    }

    public ZoneId zone(Patient patient) {
        String timezone = patient == null ? null : patient.getTimezone();
        if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            return ZoneOffset.UTC;
        }
        return ZoneId.of(timezone);
    }
}
