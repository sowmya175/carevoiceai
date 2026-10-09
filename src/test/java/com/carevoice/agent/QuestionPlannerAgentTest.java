package com.carevoice.agent;
import com.carevoice.service.MonitoringSessionContext;
import com.carevoice.service.PlannedQuestion;
import com.carevoice.service.QuestionPlannerAgent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;
import com.carevoice.service.DemoMonitoringPlans;
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

    @Test
    void usesThePlanTemplateAndStillAsksSafetyFollowUpsFirst() {
        var planned = planner.plan(
                MonitoringSessionContext.empty(),
                List.of(MonitoringField.PAIN_SCORE, MonitoringField.DIZZINESS_ONSET),
                yellow,
                DemoMonitoringPlans.postOperative().questions());

        assertThat(planned).contains(new PlannedQuestion(
                MonitoringField.DIZZINESS_ONSET,
                "When did the dizziness start?"));
    }

    @Test
    void postOperativePainWordingDiffersFromTheGeneralPlan() {
        var general = planner.plan(
                MonitoringSessionContext.empty(),
                List.of(MonitoringField.PAIN_SCORE),
                yellow,
                DemoMonitoringPlans.general().questions());
        var postOp = planner.plan(
                MonitoringSessionContext.empty(),
                List.of(MonitoringField.PAIN_SCORE),
                yellow,
                DemoMonitoringPlans.postOperative().questions());

        assertThat(general).contains(new PlannedQuestion(
                MonitoringField.PAIN_SCORE,
                "On a scale from 0 to 10, how would you rate your pain today?"));
        assertThat(postOp).contains(new PlannedQuestion(
                MonitoringField.PAIN_SCORE,
                "How would you rate your pain related to your recovery today?"));
    }
}
