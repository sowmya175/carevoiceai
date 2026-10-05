package com.carevoice.service;

import com.carevoice.agent.MissingInformationAnalyzer;
import com.carevoice.agent.PlannedQuestion;
import com.carevoice.agent.QuestionPlannerAgent;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.history.HistoryTestSupport;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.wording.QuestionWordingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptiveQuestionFlowTest {
    private static final String OPENING = "Tell me how you are feeling today in your own words.";
    private static final String ADAPTIVE_ONSET = "When did you first start feeling dizzy today?";
    private static final String ADAPTIVE_FAINTING =
            "When you felt dizzy after standing up, did you faint or lose consciousness?";

    @Test
    void adaptiveWordingDoesNotChangeTheRequestedField() {
        MonitoringSession session = session();
        session.setNextQuestion(OPENING);
        QuestionWordingService wording = mock(QuestionWordingService.class);
        when(wording.generateQuestion(any(), any())).thenReturn(ADAPTIVE_ONSET);

        var response = agent(session, wording).processMessage(1L, "I feel dizzy today and my pain is about six.");

        ArgumentCaptor<PlannedQuestion> planned = ArgumentCaptor.forClass(PlannedQuestion.class);
        verify(wording).generateQuestion(planned.capture(), any());
        assertThat(planned.getValue()).isEqualTo(new PlannedQuestion(
                MonitoringField.DIZZINESS_ONSET,
                "When did the dizziness start?"));
        assertThat(response.requestedField()).isEqualTo(MonitoringField.DIZZINESS_ONSET);
        assertThat(response.nextQuestion()).isEqualTo(ADAPTIVE_ONSET);
        assertThat(session.getRequestedField()).isEqualTo(MonitoringField.DIZZINESS_ONSET);
        assertThat(session.getNextQuestion()).isEqualTo(ADAPTIVE_ONSET);
    }

    @Test
    void shortDenialStillUsesTheRequestedField() {
        MonitoringSession session = session();
        session.setNextQuestion(OPENING);
        QuestionWordingService wording = (planned, context) -> switch (planned.field()) {
            case DIZZINESS_ONSET -> ADAPTIVE_ONSET;
            case LOSS_OF_CONSCIOUSNESS -> ADAPTIVE_FAINTING;
            default -> planned.question();
        };
        ClinicalMonitoringAgent agent = agent(session, wording);

        agent.processMessage(1L, "I feel dizzy today and my pain is about six.");
        var fainting = agent.processMessage(1L, "This morning when I stood up.");
        assertThat(fainting.requestedField()).isEqualTo(MonitoringField.LOSS_OF_CONSCIOUSNESS);
        assertThat(fainting.nextQuestion()).isEqualTo(ADAPTIVE_FAINTING);
        assertThat(session.getNextQuestion()).isEqualTo(ADAPTIVE_FAINTING);

        var medication = agent.processMessage(1L, "No.");

        assertThat(session.getLossOfConsciousness()).isFalse();
        assertThat(medication.requestedField()).isEqualTo(MonitoringField.MEDICATION_TAKEN);
        assertThat(medication.nextQuestion()).isEqualTo("Have you taken your prescribed medication today?");
    }

    @Test
    void redAndCompletedSessionsDoNotAskForWording() {
        QuestionWordingService wording = mock(QuestionWordingService.class);
        MonitoringSession red = session();
        var redResponse = agent(red, wording).processMessage(1L, "I am short of breath.");
        assertThat(redResponse.riskLevel()).isEqualTo(RiskLevel.RED);
        assertThat(redResponse.conversationComplete()).isTrue();
        assertThat(redResponse.nextQuestion()).isNull();
        assertThat(red.getRequestedField()).isNull();

        MonitoringSession complete = session();
        complete.setPainScore(2);
        complete.setDizziness(false);
        complete.setMedicationTaken(true);
        complete.setAppetite("normal");
        complete.setSleepQuality("good");
        var completed = agent(complete, wording).processMessage(2L, "Thank you.");
        assertThat(completed.conversationComplete()).isTrue();
        assertThat(completed.nextQuestion()).isNull();
        assertThat(completed.status()).isEqualTo(SessionStatus.COMPLETED);
        verify(wording, never()).generateQuestion(any(), any());
    }

    @Test
    void historyStoresTheAdaptiveQuestionThatWasShown() {
        Patient patient = new Patient("Daily Check-In", "Daily monitoring");
        HistoryTestSupport.setId(patient, 4L);
        MonitoringSession session = new MonitoringSession(patient);
        HistoryTestSupport.setId(session, 12L);
        session.setNextQuestion(OPENING);
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(12L)).thenReturn(Optional.of(session));
        when(sessions.findByIdForUpdate(12L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        QuestionWordingService wording = (planned, context) -> planned.field() == MonitoringField.DIZZINESS_ONSET
                ? ADAPTIVE_ONSET
                : planned.question();
        HistoryTestSupport history = HistoryTestSupport.capture(sessions, agent(sessions, wording), null);

        var first = history.responses.acceptText(12L, "I feel dizzy today and my pain is about six.");
        history.responses.acceptText(12L, "This morning when I stood up.");

        assertThat(first.requestedField()).isEqualTo(MonitoringField.DIZZINESS_ONSET);
        assertThat(first.nextQuestion()).isEqualTo(ADAPTIVE_ONSET);
        assertThat(history.turns.get(0).getQuestion()).isEqualTo(OPENING);
        assertThat(history.turns.get(1).getQuestion()).isEqualTo(ADAPTIVE_ONSET);
        assertThat(history.turns.get(1).getQuestion()).isNotEqualTo("When did the dizziness start?");
        assertThat(history.turns.get(1).getPatientResponse()).isEqualTo("This morning when I stood up.");
    }

    private static MonitoringSession session() {
        return new MonitoringSession(new Patient("Test", "Daily check-in"));
    }

    private static ClinicalMonitoringAgent agent(MonitoringSession session, QuestionWordingService wording) {
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(any())).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return agent(sessions, wording);
    }

    private static ClinicalMonitoringAgent agent(
            MonitoringSessionRepository sessions,
            QuestionWordingService wording) {
        return new ClinicalMonitoringAgent(
                sessions,
                new RuleBasedClinicalExtractionService(),
                new MonitoringSessionMerger(),
                new EscalationEngine(),
                new MissingInformationAnalyzer(),
                new QuestionPlannerAgent(),
                wording);
    }
}
