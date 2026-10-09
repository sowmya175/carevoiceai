package com.carevoice.reminder;

import com.carevoice.domain.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_reminder_preferences")
public class PatientReminderPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false, unique = true)
    private Patient patient;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalTime reminderTime;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected PatientReminderPreference() {}

    public static PatientReminderPreference createDefault(Patient patient, OffsetDateTime now) {
        PatientReminderPreference preference = new PatientReminderPreference();
        preference.patient = patient;
        preference.enabled = true;
        preference.reminderTime = LocalTime.of(8, 0);
        preference.createdAt = now;
        preference.updatedAt = now;
        return preference;
    }

    public void update(boolean enabled, LocalTime reminderTime, OffsetDateTime now) {
        this.enabled = enabled;
        this.reminderTime = reminderTime;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public boolean isEnabled() { return enabled; }
    public LocalTime getReminderTime() { return reminderTime; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
