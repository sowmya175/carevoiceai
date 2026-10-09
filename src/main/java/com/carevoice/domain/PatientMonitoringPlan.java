package com.carevoice.domain;

import com.carevoice.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "patient_monitoring_plans")
public class PatientMonitoringPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "monitoring_plan_id", nullable = false)
    private MonitoringPlan monitoringPlan;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Set to the patient id while this row is the active assignment.
     * Cleared when the assignment ends so only one active plan can exist per patient.
     */
    @Column(name = "active_patient_id", unique = true)
    private Long activePatientId;

    @Column(nullable = false)
    private Instant assignedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_account_id")
    private UserAccount assignedBy;

    private Instant endedAt;

    protected PatientMonitoringPlan() {}

    public PatientMonitoringPlan(Patient patient, MonitoringPlan monitoringPlan, UserAccount assignedBy) {
        if (patient.getId() == null) {
            throw new IllegalStateException("Patient must be saved before a monitoring plan is assigned.");
        }
        this.patient = patient;
        this.monitoringPlan = monitoringPlan;
        this.assignedBy = assignedBy;
        this.active = true;
        this.activePatientId = patient.getId();
        this.assignedAt = Instant.now();
    }

    public void end() {
        this.active = false;
        this.activePatientId = null;
        this.endedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public MonitoringPlan getMonitoringPlan() { return monitoringPlan; }
    public boolean isActive() { return active; }
    public Instant getAssignedAt() { return assignedAt; }
    public UserAccount getAssignedBy() { return assignedBy; }
    public Instant getEndedAt() { return endedAt; }
}
