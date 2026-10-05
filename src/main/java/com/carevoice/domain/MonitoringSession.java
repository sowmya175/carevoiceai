package com.carevoice.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "monitoring_sessions")
public class MonitoringSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel = RiskLevel.GREEN;

    private Integer painScore;
    private Boolean dizziness;

    @Column(length = 2000)
    private String dizzinessOnset;

    private Boolean lossOfConsciousness;
    private Boolean medicationTaken;
    private Boolean appetiteReduced;

    @Column(length = 255)
    private String appetite;

    @Column(length = 255)
    private String sleepQuality;

    private Boolean shortnessOfBreath;
    private Double temperature;

    @Enumerated(EnumType.STRING)
    private MonitoringField requestedField;

    private Integer questionsAsked;

    @Enumerated(EnumType.STRING)
    private MonitoringField clarifiedField;

    @ElementCollection
    @CollectionTable(name = "monitoring_session_deferred_fields", joinColumns = @JoinColumn(name = "session_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false)
    private Set<MonitoringField> deferredFields = new HashSet<>();

    @Column(length = 4000)
    private String latestTranscript;

    @Column(length = 2000)
    private String nextQuestion;

    @Column(length = 2000)
    private String escalationReason;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected MonitoringSession() {}

    public MonitoringSession(Patient patient) {
        this.patient = patient;
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public SessionStatus getStatus() { return status; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public Integer getPainScore() { return painScore; }
    public Boolean getDizziness() { return dizziness; }
    public String getDizzinessOnset() { return dizzinessOnset; }
    public Boolean getLossOfConsciousness() { return lossOfConsciousness; }
    public Boolean getMedicationTaken() { return medicationTaken; }
    public Boolean getAppetiteReduced() { return appetiteReduced; }
    public String getAppetite() { return appetite; }
    public String getSleepQuality() { return sleepQuality; }
    public Boolean getShortnessOfBreath() { return shortnessOfBreath; }
    public Double getTemperature() { return temperature; }
    public MonitoringField getRequestedField() { return requestedField; }
    public int getQuestionsAsked() { return questionsAsked == null ? 0 : questionsAsked; }
    public MonitoringField getClarifiedField() { return clarifiedField; }
    public boolean isDeferred(MonitoringField field) { return deferredFields.contains(field); }
    public void deferField(MonitoringField field) { deferredFields.add(field); touch(); }
    public String getLatestTranscript() { return latestTranscript; }
    public String getNextQuestion() { return nextQuestion; }
    public String getEscalationReason() { return escalationReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(SessionStatus status) { this.status = status; touch(); }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; touch(); }
    public void setPainScore(Integer painScore) { this.painScore = painScore; touch(); }
    public void setDizziness(Boolean dizziness) { this.dizziness = dizziness; touch(); }
    public void setDizzinessOnset(String dizzinessOnset) { this.dizzinessOnset = dizzinessOnset; touch(); }
    public void setLossOfConsciousness(Boolean value) { this.lossOfConsciousness = value; touch(); }
    public void setMedicationTaken(Boolean medicationTaken) { this.medicationTaken = medicationTaken; touch(); }
    public void setAppetiteReduced(Boolean appetiteReduced) { this.appetiteReduced = appetiteReduced; touch(); }
    public void setAppetite(String appetite) { this.appetite = appetite; touch(); }
    public void setSleepQuality(String sleepQuality) { this.sleepQuality = sleepQuality; touch(); }
    public void setShortnessOfBreath(Boolean shortnessOfBreath) { this.shortnessOfBreath = shortnessOfBreath; touch(); }
    public void setTemperature(Double temperature) { this.temperature = temperature; touch(); }
    public void setRequestedField(MonitoringField requestedField) { this.requestedField = requestedField; touch(); }
    public void setQuestionsAsked(int questionsAsked) { this.questionsAsked = questionsAsked; touch(); }
    public void setClarifiedField(MonitoringField clarifiedField) { this.clarifiedField = clarifiedField; touch(); }
    public void setLatestTranscript(String latestTranscript) { this.latestTranscript = latestTranscript; touch(); }
    public void setNextQuestion(String nextQuestion) { this.nextQuestion = nextQuestion; touch(); }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; touch(); }

    private void touch() { this.updatedAt = OffsetDateTime.now(); }
}
