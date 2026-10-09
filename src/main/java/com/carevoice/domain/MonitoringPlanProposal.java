package com.carevoice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "monitoring_plan_proposals")
public class MonitoringPlanProposal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "requested_by_account_id", nullable = false)
    private Long requestedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status = ProposalStatus.DRAFT;

    @ElementCollection
    @CollectionTable(name = "monitoring_plan_proposal_families", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    @Column(name = "family_label", nullable = false, length = 80)
    private List<String> conditionFamilies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "monitoring_plan_proposal_warnings", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    @Column(name = "warning_text", nullable = false, length = 200)
    private List<String> warnings = new ArrayList<>();

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("primaryCondition DESC, conditionName ASC")
    private List<MonitoringPlanProposalCondition> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<MonitoringPlanProposalQuestion> questions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_monitoring_plan_id")
    private MonitoringPlan approvedPlan;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = createdAt;

    private Instant approvedAt;
    private Instant rejectedAt;

    @Column(name = "generation_provider", length = 40)
    private String generationProvider;

    @Column(name = "generation_model", length = 300)
    private String generationModel;

    @Column(name = "dataset_version", length = 80)
    private String datasetVersion;

    @Column(name = "task_contract_version", length = 80)
    private String taskContractVersion;

    protected MonitoringPlanProposal() {}

    public MonitoringPlanProposal(Patient patient, Long requestedByAccountId) {
        this.patient = patient;
        this.requestedByAccountId = requestedByAccountId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void addCondition(MonitoringPlanProposalCondition condition) {
        conditions.add(condition);
    }

    public void addQuestion(MonitoringPlanProposalQuestion question) {
        questions.add(question);
    }

    public void replaceFamilies(List<String> families) {
        conditionFamilies.clear();
        conditionFamilies.addAll(families);
    }

    public void replaceWarnings(List<String> messages) {
        warnings.clear();
        warnings.addAll(messages);
    }

    public void supersede() {
        this.status = ProposalStatus.SUPERSEDED;
        touch();
    }

    public void approve(MonitoringPlan plan) {
        this.status = ProposalStatus.APPROVED;
        this.approvedPlan = plan;
        this.approvedAt = Instant.now();
        touch();
    }

    public void reject() {
        this.status = ProposalStatus.REJECTED;
        this.rejectedAt = Instant.now();
        touch();
    }

    public void recordGeneration(String provider, String model, String datasetVersion, String taskContractVersion) {
        this.generationProvider = limit(provider, 40);
        this.generationModel = limit(model, 300);
        this.datasetVersion = limit(datasetVersion, 80);
        this.taskContractVersion = limit(taskContractVersion, 80);
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public Long getRequestedByAccountId() { return requestedByAccountId; }
    public ProposalStatus getStatus() { return status; }
    public List<String> getConditionFamilies() { return conditionFamilies; }
    public List<String> getWarnings() { return warnings; }
    public List<MonitoringPlanProposalCondition> getConditions() { return conditions; }
    public List<MonitoringPlanProposalQuestion> getQuestions() { return questions; }
    public MonitoringPlan getApprovedPlan() { return approvedPlan; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public String getGenerationProvider() { return generationProvider; }
    public String getGenerationModel() { return generationModel; }
    public String getDatasetVersion() { return datasetVersion; }
    public String getTaskContractVersion() { return taskContractVersion; }
}
