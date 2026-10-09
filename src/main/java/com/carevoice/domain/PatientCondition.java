package com.carevoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "patient_conditions")
public class PatientCondition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, length = 255)
    private String conditionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MonitoringCategory monitoringCategory;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "primary_condition", nullable = false)
    private boolean primaryCondition;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = createdAt;

    protected PatientCondition() {}

    public PatientCondition(
            Patient patient,
            String conditionName,
            MonitoringCategory monitoringCategory,
            boolean active,
            boolean primaryCondition) {
        this.patient = patient;
        this.conditionName = conditionName;
        this.monitoringCategory = monitoringCategory;
        this.active = active;
        this.primaryCondition = primaryCondition;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public void rename(String conditionName) { this.conditionName = conditionName; }
    public void categorize(MonitoringCategory monitoringCategory) { this.monitoringCategory = monitoringCategory; }
    public void setActive(boolean active) { this.active = active; }
    public void setPrimaryCondition(boolean primaryCondition) { this.primaryCondition = primaryCondition; }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public String getConditionName() { return conditionName; }
    public MonitoringCategory getMonitoringCategory() { return monitoringCategory; }
    public boolean isActive() { return active; }
    public boolean isPrimaryCondition() { return primaryCondition; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
