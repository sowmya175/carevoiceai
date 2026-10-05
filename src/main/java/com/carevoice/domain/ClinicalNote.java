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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "clinical_notes")
public class ClinicalNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(unique = true, nullable = false)
    private MonitoringTurn monitoringTurn;

    @Column(length = 4000)
    private String noteText;

    @Column(nullable = false, length = 4000)
    private String extractedFactsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(length = 2000)
    private String escalationReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 32)
    private String noteProvider;

    @Column(length = 64)
    private String model;

    protected ClinicalNote() {}

    public ClinicalNote(
            MonitoringTurn monitoringTurn,
            String extractedFactsJson,
            RiskLevel riskLevel,
            String escalationReason) {
        this.monitoringTurn = monitoringTurn;
        this.extractedFactsJson = extractedFactsJson;
        this.riskLevel = riskLevel;
        this.escalationReason = escalationReason;
        this.createdAt = monitoringTurn.getCreatedAt();
    }

    public Long getId() { return id; }
    public MonitoringTurn getMonitoringTurn() { return monitoringTurn; }
    public String getNoteText() { return noteText; }
    public String getExtractedFactsJson() { return extractedFactsJson; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getEscalationReason() { return escalationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public String getNoteProvider() { return noteProvider; }
    public String getModel() { return model; }

    public void setNoteText(String noteText) { this.noteText = noteText; }
    public void setNoteProvider(String noteProvider) { this.noteProvider = noteProvider; }
    public void setModel(String model) { this.model = model; }
}
