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

@Entity
@Table(
        name = "monitoring_plan_questions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"monitoring_plan_id", "monitoring_field"})
)
public class MonitoringPlanQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "monitoring_plan_id", nullable = false)
    private MonitoringPlan monitoringPlan;

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
    private boolean required = true;

    @Column(nullable = false)
    private boolean active = true;

    protected MonitoringPlanQuestion() {}

    public MonitoringPlanQuestion(MonitoringPlan monitoringPlan, PlanField field) {
        this.monitoringPlan = monitoringPlan;
        this.monitoringField = field.field();
        this.questionTemplate = field.questionTemplate();
        this.clarificationTemplate = field.clarificationTemplate();
        this.displayOrder = field.displayOrder();
        this.required = field.required();
        this.active = true;
    }

    public PlanField toPlanField() {
        return new PlanField(monitoringField, questionTemplate, clarificationTemplate, displayOrder, required && active);
    }

    public Long getId() { return id; }
    public MonitoringPlan getMonitoringPlan() { return monitoringPlan; }
    public MonitoringField getMonitoringField() { return monitoringField; }
    public String getQuestionTemplate() { return questionTemplate; }
    public String getClarificationTemplate() { return clarificationTemplate; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isRequired() { return required; }
    public boolean isActive() { return active; }
}
