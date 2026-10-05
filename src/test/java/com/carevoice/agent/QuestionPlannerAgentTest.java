package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;
import com.carevoice.service.EscalationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionPlannerAgentTest {
    private final QuestionPlannerAgent planner = new QuestionPlannerAgent();
    private final EscalationEngine.Evaluation yellow =
            new EscalationEngine.Evaluation(RiskLevel.YELLOW, "prototype review");

    @Test
    void selectsSafetyRelevantMissingFieldsBeforeRoutineFields() {
        var planned = planner.plan(
                MonitoringSessionContext.empty(),
                List.of(MonitoringField.PAIN_SCORE, MonitoringField.APPETITE, MonitoringField.LOSS_OF_CONSCIOUSNESS),
                yellow);

        assertThat(planned).contains(new PlannedQuestion(
                MonitoringField.LOSS_OF_CONSCIOUSNESS,
                "Did you faint or lose consciousness?"));
    }

    @Test
    void returnsOnlyOneNextQuestion() {
        var planned = planner.plan(
                MonitoringSessionContext.empty(),
                List.of(MonitoringField.MEDICATION_TAKEN, MonitoringField.APPETITE, MonitoringField.SLEEP_QUALITY),
                yellow);

        assertThat(planned).contains(new PlannedQuestion(
                MonitoringField.MEDICATION_TAKEN,
                "Have you taken your prescribed medication today?"));
    }

    @Test
    void returnsNoQuestionWhenRequiredInformationIsComplete() {
        var planned = planner.plan(
                MonitoringSessionContext.empty(),
                List.of(),
                new EscalationEngine.Evaluation(RiskLevel.GREEN, null));

        assertThat(planned).isEmpty();
    }

    @Test
    void asksDizzinessOnsetBeforeLossOfConsciousness() {
        var planned = planner.plan(
                MonitoringSessionContext.empty(),
                List.of(MonitoringField.LOSS_OF_CONSCIOUSNESS, MonitoringField.DIZZINESS_ONSET, MonitoringField.SLEEP_QUALITY),
                yellow);

        assertThat(planned).contains(new PlannedQuestion(
                MonitoringField.DIZZINESS_ONSET,
                "When did the dizziness start?"));
    }
}
