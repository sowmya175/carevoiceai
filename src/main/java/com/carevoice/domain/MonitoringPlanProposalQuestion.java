package com.carevoice.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "monitoring_plan_proposal_questions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"proposal_id", "field_definition_id"})
)
public class MonitoringPlanProposalQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private MonitoringPlanProposal proposal;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "field_definition_id", nullable = false)
    private MonitoringFieldDefinition fieldDefinition;

    @Column(nullable = false, length = 500)
    private String questionText;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean required = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 500)
    private String rationale;

    @ElementCollection
    @CollectionTable(
            name = "monitoring_plan_proposal_question_conditions",
            joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "position")
    @Column(name = "source_patient_condition_id")
    private List<Long> relevantConditionIds = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = createdAt;

    protected MonitoringPlanProposalQuestion() {}

    public MonitoringPlanProposalQuestion(
            MonitoringPlanProposal proposal,
            MonitoringFieldDefinition fieldDefinition,
            String questionText,
            int displayOrder,
            boolean required,
            String rationale,
            List<Long> relevantConditionIds) {
        this.proposal = proposal;
        this.fieldDefinition = fieldDefinition;
        this.questionText = questionText;
        this.displayOrder = displayOrder;
        this.required = required;
        this.enabled = true;
        this.rationale = rationale;
        this.relevantConditionIds = new ArrayList<>(relevantConditionIds);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void revise(String questionText, boolean required, boolean enabled) {
        this.questionText = questionText;
        this.required = required;
        this.enabled = enabled;
    }

    public void place(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public MonitoringFieldDefinition getFieldDefinition() { return fieldDefinition; }
    public String getQuestionText() { return questionText; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isRequired() { return required; }
    public boolean isEnabled() { return enabled; }
    public String getRationale() { return rationale; }
    public List<Long> getRelevantConditionIds() { return relevantConditionIds; }
}
