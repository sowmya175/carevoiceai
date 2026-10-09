package com.carevoice.domain;

import com.carevoice.plan.PlanField;
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
import jakarta.persistence.UniqueConstraint;

/**
 * Copy of one plan question taken when a monitoring session starts.
 * Later changes to the patient's assignment do not alter this row.
 */
@Entity
@Table(
        name = "session_plan_questions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "monitoring_field"})
)
public class SessionPlanQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private MonitoringSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "monitoring_field", nullable = false, length = 40)
    private MonitoringField monitoringField;

    @Column(nullable = false, length = 500)
    private String questionTemplate;

    @Column(length = 500)
    private String clarificationTemplate;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean required;

    protected SessionPlanQuestion() {}

    public SessionPlanQuestion(MonitoringSession session, PlanField field) {
        this.session = session;
        this.monitoringField = field.field();
        this.questionTemplate = field.questionTemplate();
        this.clarificationTemplate = field.clarificationTemplate();
        this.displayOrder = field.displayOrder();
        this.required = field.required();
    }

    public PlanField toPlanField() {
        return new PlanField(monitoringField, questionTemplate, clarificationTemplate, displayOrder, required);
    }

    public void setSession(MonitoringSession session) { this.session = session; }
    public MonitoringField getMonitoringField() { return monitoringField; }
    public String getQuestionTemplate() { return questionTemplate; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isRequired() { return required; }
}
