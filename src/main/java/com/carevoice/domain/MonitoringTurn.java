package com.carevoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "monitoring_turns",
        uniqueConstraints = @UniqueConstraint(columnNames = {"monitoring_session_id", "sequence_number"})
)
public class MonitoringTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Patient patient;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MonitoringSession monitoringSession;

    @Column(nullable = false)
    private int sequenceNumber;

    @Column(length = 2000)
    private String question;

    @Column(nullable = false, length = 4000)
    private String patientResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InputMode inputMode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected MonitoringTurn() {}

    public MonitoringTurn(
            Patient patient,
            MonitoringSession monitoringSession,
            int sequenceNumber,
            String question,
            String patientResponse,
            InputMode inputMode) {
        this.patient = patient;
        this.monitoringSession = monitoringSession;
        this.sequenceNumber = sequenceNumber;
        this.question = question;
        this.patientResponse = patientResponse;
        this.inputMode = inputMode;
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public MonitoringSession getMonitoringSession() { return monitoringSession; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getQuestion() { return question; }
    public String getPatientResponse() { return patientResponse; }
    public InputMode getInputMode() { return inputMode; }
    public Instant getCreatedAt() { return createdAt; }
}
