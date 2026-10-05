package com.carevoice.service;

import com.carevoice.agent.MissingInformationAnalyzer;
import com.carevoice.agent.QuestionPlannerAgent;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.repository.MonitoringSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicalMonitoringAgentTest {

    @Test
    void processesAMultiTurnConversationWithoutForgettingEarlierFacts() {
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Daily check-in"));
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalMonitoringAgent agent = new ClinicalMonitoringAgent(
                sessions,
                new RuleBasedClinicalExtractionService(),
                new MonitoringSessionMerger(),
                new EscalationEngine(),
                new MissingInformationAnalyzer(),
                new QuestionPlannerAgent());

        var first = agent.processMessage(1L, "My pain is 6 and I feel dizzy.");

        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
        assertThat(first.requestedField()).isEqualTo(MonitoringField.DIZZINESS_ONSET);
        assertThat(first.nextQuestion()).isEqualTo("When did the dizziness start?");
        assertThat(first.riskLevel()).isEqualTo(RiskLevel.YELLOW);
        assertThat(first.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(first.conversationComplete()).isFalse();

        var second = agent.processMessage(1L, "It started this morning.");

        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
        assertThat(session.getDizzinessOnset()).isEqualTo("this morning");
        assertThat(second.requestedField()).isEqualTo(MonitoringField.LOSS_OF_CONSCIOUSNESS);
        assertThat(second.nextQuestion()).isEqualTo("Did you faint or lose consciousness?");
        assertThat(second.collectedFacts().painScore()).isEqualTo(6);
        assertThat(second.collectedFacts().dizziness()).isTrue();

        var third = agent.processMessage(1L, "No, I didn't faint.");

        assertThat(session.getLossOfConsciousness()).isFalse();
        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
        assertThat(session.getDizzinessOnset()).isEqualTo("this morning");
        assertThat(third.requestedField()).isEqualTo(MonitoringField.MEDICATION_TAKEN);
        assertThat(third.nextQuestion()).isEqualTo("Have you taken your prescribed medication today?");
        assertThat(third.conversationComplete()).isFalse();
        assertThat(third.missingFields()).containsExactly(
                MonitoringField.MEDICATION_TAKEN,
                MonitoringField.APPETITE,
                MonitoringField.SLEEP_QUALITY);
    }
}
