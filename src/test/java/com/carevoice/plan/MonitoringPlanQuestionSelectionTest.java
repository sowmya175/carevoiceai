package com.carevoice.plan;

import com.carevoice.agent.MissingInformationAnalyzer;
import com.carevoice.agent.QuestionPlannerAgent;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringPlan;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.service.ClinicalMonitoringAgent;
import com.carevoice.service.EscalationEngine;
import com.carevoice.service.MonitoringSessionMerger;
import com.carevoice.wording.QuestionWordingService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitoringPlanQuestionSelectionTest {
    @Test
    void theSameOpeningProducesDifferentQuestionsForDifferentPlans() {
        var general = ask(DemoMonitoringPlans.general(), (planned, context) -> planned.question());
        var postOp = ask(DemoMonitoringPlans.postOperative(), (planned, context) -> planned.question());

        assertThat(general.requestedField()).isEqualTo(MonitoringField.PAIN_SCORE);
        assertThat(postOp.requestedField()).isEqualTo(MonitoringField.PAIN_SCORE);
        assertThat(general.nextQuestion()).isEqualTo("On a scale from 0 to 10, how would you rate your pain today?");
        assertThat(postOp.nextQuestion()).isEqualTo("How would you rate your pain related to your recovery today?");
        assertThat(general.missingFields()).doesNotContain(MonitoringField.TEMPERATURE);
        assertThat(postOp.missingFields()).contains(MonitoringField.TEMPERATURE);
    }

    @Test
    void adaptiveWordingCanRephraseWithoutChangingTheField() {
        var response = ask(DemoMonitoringPlans.postOperative(),
                (planned, context) -> "How is the pain from your recovery feeling today?");

        assertThat(response.requestedField()).isEqualTo(MonitoringField.PAIN_SCORE);
        assertThat(response.nextQuestion()).isEqualTo("How is the pain from your recovery feeling today?");
    }

    private static com.carevoice.agent.ClinicalAgentResponse ask(
            DemoMonitoringPlans.Template template,
            QuestionWordingService wording) {
        MonitoringSession session = new MonitoringSession(new Patient("Sarah", "Daily monitoring"));
        session.capturePlan(
                new MonitoringPlan(template.code(), template.name(), template.description(), template.conditionLabel()),
                template.questions());
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new ClinicalMonitoringAgent(
                sessions,
                new RuleBasedClinicalExtractionService(),
                new MonitoringSessionMerger(),
                new EscalationEngine(),
                new MissingInformationAnalyzer(),
                new QuestionPlannerAgent(),
                wording).processMessage(1L, "I feel okay today.");
    }
}
