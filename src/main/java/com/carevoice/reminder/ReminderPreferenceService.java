package com.carevoice.reminder;

import com.carevoice.domain.Patient;
import com.carevoice.repository.PatientRepository;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * One reminder preference per patient. The profile timezone stays on {@code Patient} and is not stored here.
 * New patients default to 08:00 local time with reminders enabled. That default is for in-app delivery only.
 * It is not consent for SMS, email, or push notifications.
 */
@Service
public class ReminderPreferenceService {
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DEFAULT_TIME = LocalTime.of(8, 0);

    private final PatientReminderPreferenceRepository preferences;
    private final PatientRepository patients;
    private final Clock clock;

    public ReminderPreferenceService(
            PatientReminderPreferenceRepository preferences,
            PatientRepository patients,
            Clock clock) {
        this.preferences = preferences;
        this.patients = patients;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReminderPreferenceResponse get(Long patientId) {
        Patient patient = requirePatient(patientId);
        return preferences.findByPatient_Id(patientId)
                .map(preference -> ReminderPreferenceResponse.of(preference.isEnabled(), preference.getReminderTime(), timezone(patient)))
                .orElseGet(() -> ReminderPreferenceResponse.of(true, DEFAULT_TIME, timezone(patient)));
    }

    @Transactional
    public ReminderPreferenceResponse update(Long patientId, Boolean enabled, String reminderTime) {
        if (enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose whether reminders are enabled.");
        }
        LocalTime time = parseTime(reminderTime);
        Patient patient = requirePatient(patientId);
        OffsetDateTime now = now();
        PatientReminderPreference preference = preferences.findByPatient_Id(patientId)
                .orElseGet(() -> PatientReminderPreference.createDefault(patient, now));
        preference.update(enabled, time, now);
        return ReminderPreferenceResponse.of(preferences.save(preference).isEnabled(), time, timezone(patient));
    }

    @Transactional
    public void ensureDefault(Patient patient) {
        if (patient == null || patient.getId() == null || preferences.findByPatient_Id(patient.getId()).isPresent()) {
            return;
        }
        preferences.save(PatientReminderPreference.createDefault(patient, now()));
    }

    private Patient requirePatient(Long patientId) {
        return patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
    }

    private String timezone(Patient patient) {
        String stored = patient.getTimezone();
        if (stored != null && ZoneId.getAvailableZoneIds().contains(stored)) {
            return stored;
        }
        return "UTC";
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    static LocalTime parseTime(String reminderTime) {
        if (reminderTime == null || reminderTime.isBlank()) {
            throw invalidTime();
        }
        try {
            return LocalTime.parse(reminderTime.trim(), DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException ex) {
            throw invalidTime();
        }
    }

    private static ResponseStatusException invalidTime() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid reminder time.");
    }

    public record ReminderPreferenceResponse(boolean enabled, String reminderTime, String timezone) {
        static ReminderPreferenceResponse of(boolean enabled, LocalTime reminderTime, String timezone) {
            return new ReminderPreferenceResponse(enabled, reminderTime.format(HOUR_MINUTE), timezone);
        }
    }
}
