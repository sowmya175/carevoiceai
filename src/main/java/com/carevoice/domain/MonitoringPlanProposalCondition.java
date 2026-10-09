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
import jakarta.persistence.Table;

/**
 * Snapshot of the active conditions used when a proposal was generated.
 * Later edits to the patient's conditions do not rewrite this row.
 */
@Entity
@Table(name = "monitoring_plan_proposal_conditions")
public class MonitoringPlanProposalCondition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private MonitoringPlanProposal proposal;

    @Column(name = "source_patient_condition_id")
    private Long sourcePatientConditionId;

    @Column(nullable = false, length = 255)
    private String conditionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MonitoringCategory monitoringCategory;

    @Column(name = "primary_condition", nullable = false)
    private boolean primaryCondition;

    protected MonitoringPlanProposalCondition() {}

    public MonitoringPlanProposalCondition(
            MonitoringPlanProposal proposal,
            Long sourcePatientConditionId,
            String conditionName,
            MonitoringCategory monitoringCategory,
            boolean primaryCondition) {
        this.proposal = proposal;
        this.sourcePatientConditionId = sourcePatientConditionId;
        this.conditionName = conditionName;
        this.monitoringCategory = monitoringCategory;
        this.primaryCondition = primaryCondition;
    }

    public Long getId() { return id; }
    public Long getSourcePatientConditionId() { return sourcePatientConditionId; }
    public String getConditionName() { return conditionName; }
    public MonitoringCategory getMonitoringCategory() { return monitoringCategory; }
    public boolean isPrimaryCondition() { return primaryCondition; }
}
