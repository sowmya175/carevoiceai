package com.carevoice.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    /**
     * Legacy free-text label. It mirrors the active primary {@code PatientCondition}
     * and does not select questions. The active {@link PatientMonitoringPlan} assignment does.
     */
    @Column(nullable = false)
    private String monitoringPlan;

    // Nullable additions preserve patients created before account registration existed.
    @Column(length = 255)
    private String medicalCondition;
    @Column(length = 64)
    private String timezone;
    private Instant createdAt;
    private Instant updatedAt;

    protected Patient() {}

    public Patient(String displayName, String monitoringPlan) {
        this.displayName = displayName;
        this.monitoringPlan = monitoringPlan;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Patient(String fullName, String medicalCondition, String timezone) {
        this(fullName, "Daily monitoring");
        this.medicalCondition = medicalCondition;
        this.timezone = timezone;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public void setMedicalCondition(String medicalCondition) {
        this.medicalCondition = medicalCondition;
        this.updatedAt = Instant.now();
    }

    public String getFullName() { return displayName; }
    public String getMedicalCondition() { return medicalCondition; }
    public String getTimezone() { return timezone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getMonitoringPlan() { return monitoringPlan; }
}
