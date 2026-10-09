package com.carevoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "monitoring_plans")
public class MonitoringPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 160)
    private String conditionLabel;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Null means a shared demo template. Patient-specific plans set {@link PlanOrigin#PATIENT_SPECIFIC}
     * and {@link #ownerPatientId}. A missing column value stays compatible with existing rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PlanOrigin origin;

    @Column(name = "owner_patient_id")
    private Long ownerPatientId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = createdAt;

    protected MonitoringPlan() {}

    public MonitoringPlan(String code, String name, String description, String conditionLabel) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.conditionLabel = conditionLabel;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getConditionLabel() { return conditionLabel; }
    public void assignOwner(Long patientId) {
        this.origin = PlanOrigin.PATIENT_SPECIFIC;
        this.ownerPatientId = patientId;
    }

    public boolean isActive() { return active; }
    public PlanOrigin getOrigin() { return origin; }
    public Long getOwnerPatientId() { return ownerPatientId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
