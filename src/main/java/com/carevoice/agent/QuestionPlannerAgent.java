package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;
import com.carevoice.service.EscalationEngine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class QuestionPlannerAgent {

    private static final Map<MonitoringField, String> QUESTIONS = Map.of(
            MonitoringField.PAIN_SCORE, "On a scale from 0 to 10, how would you rate your pain today?",
            MonitoringField.MEDICATION_TAKEN, "Have you taken your prescribed medication today?",
            MonitoringField.APPETITE, "How has your appetite been today?",
            MonitoringField.SLEEP_QUALITY, "How did you sleep last night?",
            MonitoringField.DIZZINESS_ONSET, "When did the dizziness start?",
            MonitoringField.LOSS_OF_CONSCIOUSNESS, "Did you faint or lose consciousness?",
            MonitoringField.TEMPERATURE, "What is your temperature reading?"
    );

    public Optional<PlannedQuestion> plan(
            MonitoringSessionContext context,
            List<MonitoringField> missingFields,
            EscalationEngine.Evaluation escalation) {
        if (escalation != null && escalation.riskLevel() == RiskLevel.RED) {
            return Optional.empty();
        }
        if (missingFields == null || missingFields.isEmpty()) {
            return Optional.empty();
        }
        for (MonitoringField field : MonitoringField.priorityOrder()) {
            if (missingFields.contains(field)) {
                return Optional.of(new PlannedQuestion(field, QUESTIONS.get(field)));
            }
        }
        return Optional.empty();
    }
}
