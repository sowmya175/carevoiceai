package com.carevoice.service;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

import com.carevoice.domain.*;
import com.carevoice.repository.MonitoringSessionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DirectedClarificationTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "MEDICATION_TAKEN|Okay.|I didn't catch whether you took your medication today. Did you take it?",
            "APPETITE|Could be better.|Would you say your appetite today has been good, normal, reduced, or poor?",
            "SLEEP_QUALITY|Not great.|Would you say your sleep was good, normal, or poor?",
            "LOSS_OF_CONSCIOUSNESS|I almost fainted.|To confirm, did you actually faint or lose consciousness?",
            "LOSS_OF_CONSCIOUSNESS|I felt like I might pass out.|To confirm, did you actually faint or lose consciousness?",
            "DIZZINESS_ONSET|Okay.|I didn't catch when the dizziness started. When did it begin?",
            "TEMPERATURE|200|I didn't catch the temperature reading. What number does your thermometer show?",
            "TEMPERATURE|Not sure.|I didn't catch the temperature reading. What number does your thermometer show?"
    })
    void clarifiesUnknownDirectedAnswersWithoutAdaptiveWording(MonitoringField field, String answer, String question) {
        MonitoringSession session = session(field);
        QuestionWordingService wording = mock(QuestionWordingService.class);

        var response = agent(session, wording).processMessage(1L, answer);

        assertThat(QuestionClarification.isUnknown(field, response.collectedFacts())).isTrue();
        assertThat(response.requestedField()).isEqualTo(field);
        assertThat(response.nextQuestion()).isEqualTo(question);
        assertThat(response.missingFields()).contains(field);
        assertThat(session.getClarifiedField()).isEqualTo(field);
        assertThat(session.getLatestTranscript()).isEqualTo(answer);
        verifyNoInteractions(wording);
    }

    @ParameterizedTest
    @EnumSource(MonitoringField.class)
    void everyFieldGetsOnlyOneClarificationThenIsDeferred(MonitoringField field) {
        MonitoringSession session = session(field);
        ClinicalMonitoringAgent agent = agent(session, (planned, context) -> planned.question());
        var first = agent.processMessage(1L, "Okay.");
        var second = agent.processMessage(1L, "Okay.");
        assertThat(first.requestedField()).isEqualTo(field);
        assertThat(second.requestedField()).isNotEqualTo(field);
        assertThat(second.missingFields()).contains(field);
        assertThat(session.isDeferred(field)).isTrue();
        assertThat(session.getClarifiedField()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "MEDICATION_TAKEN|Yes.", "APPETITE|Normal.", "SLEEP_QUALITY|Good.",
            "LOSS_OF_CONSCIOUSNESS|No.", "DIZZINESS_ONSET|This morning.", "TEMPERATURE|98.6", "PAIN_SCORE|3"
    })
    void successfulClarificationCollectsTheAnswerAndAdvances(MonitoringField field, String answer) {
        MonitoringSession session = session(field);
        ClinicalMonitoringAgent agent = agent(session, (planned, context) -> planned.question());
        agent.processMessage(1L, "Okay.");
        var response = agent.processMessage(1L, answer);
        assertThat(QuestionClarification.isUnknown(field, response.collectedFacts())).isFalse();
        assertThat(response.requestedField()).isNotEqualTo(field);
        assertThat(session.isDeferred(field)).isFalse();
        assertThat(session.getClarifiedField()).isNull();
    }

    @Test
    void skippingSeveralFieldsNeverRevisitsAnEarlierDeferredField() {
        MonitoringSession session = session(MonitoringField.DIZZINESS_ONSET);
        ClinicalMonitoringAgent agent = agent(session, (planned, context) -> planned.question());
        for (MonitoringField field : MonitoringField.priorityOrder()) {
            if (field == MonitoringField.TEMPERATURE) continue; // Optional, not part of the daily plan.
            assertThat(session.getRequestedField()).isEqualTo(field);
            assertThat(agent.processMessage(1L, "Okay.").requestedField()).isEqualTo(field);
            var next = agent.processMessage(1L, "Okay.");
            assertThat(next.requestedField()).isNotEqualTo(field);
            assertThat(session.isDeferred(field)).isTrue();
        }
        assertThat(session.getRequestedField()).isNull();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY_FOR_REVIEW);
        assertThat(session.getQuestionsAsked()).isEqualTo(11);
    }

    @Test
    void actualLossOfConsciousnessStillEscalatesInsteadOfClarifying() {
        MonitoringSession session = session(MonitoringField.LOSS_OF_CONSCIOUSNESS);
        QuestionWordingService wording = mock(QuestionWordingService.class);
        var response = agent(session, wording).processMessage(1L, "Yes, I passed out.");
        assertThat(session.getLossOfConsciousness()).isTrue();
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.RED);
        assertThat(response.conversationComplete()).isTrue();
        verifyNoInteractions(wording);
    }

    private static MonitoringSession session(MonitoringField field) {
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Daily monitoring"));
        session.setDizziness(true);
        session.setRequestedField(field);
        session.setNextQuestion("Normal question");
        return session;
    }

    private static ClinicalMonitoringAgent agent(MonitoringSession session, QuestionWordingService wording) {
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        return new ClinicalMonitoringAgent(sessions, new RuleBasedClinicalExtractionService(),
                new MonitoringSessionMerger(), new EscalationEngine(), new MissingInformationAnalyzer(),
                new QuestionPlannerAgent(), wording);
    }
}
