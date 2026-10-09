package com.carevoice.service;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;

import com.carevoice.domain.PlanField;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class QuestionPlannerAgent {
    private static final List<MonitoringField> SAFETY_FOLLOW_UPS = List.of(
            MonitoringField.DIZZINESS_ONSET,
            MonitoringField.LOSS_OF_CONSCIOUSNESS);

    public Optional<PlannedQuestion> plan(
            MonitoringSessionContext context,
            List<MonitoringField> missingFields,
            EscalationEngine.Evaluation escalation) {
        return plan(context, missingFields, escalation, DemoMonitoringPlans.general().questions());
    }

    /**
     * Safety follow-ups are asked before the next routine plan question.
     * Routine order comes from the session plan, not from a universal field list.
     */
    public Optional<PlannedQuestion> plan(
            MonitoringSessionContext context,
            List<MonitoringField> missingFields,
            EscalationEngine.Evaluation escalation,
            List<PlanField> planFields) {
        if (escalation != null && escalation.riskLevel() == RiskLevel.RED) {
            return Optional.empty();
        }
        if (missingFields == null || missingFields.isEmpty()) {
            return Optional.empty();
        }
        for (MonitoringField field : SAFETY_FOLLOW_UPS) {
            if (missingFields.contains(field)) {
                return Optional.of(new PlannedQuestion(field, questionText(planFields, field)));
            }
        }
        List<PlanField> ordered = planFields == null ? List.of() : planFields.stream()
                .sorted(Comparator.comparingInt(PlanField::displayOrder))
                .toList();
        for (PlanField item : ordered) {
            if (missingFields.contains(item.field())) {
                return Optional.of(new PlannedQuestion(item.field(), item.questionTemplate()));
            }
        }
        return Optional.empty();
    }

    private static String questionText(List<PlanField> planFields, MonitoringField field) {
        if (planFields != null) {
            for (PlanField item : planFields) {
                if (item.field() == field && item.questionTemplate() != null && !item.questionTemplate().isBlank()) {
                    return item.questionTemplate();
                }
            }
        }
        return DemoMonitoringPlans.defaultQuestion(field);
    }
}
