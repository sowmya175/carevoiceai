package com.carevoice.service;

import com.carevoice.agent.MissingInformationAnalyzer;
import com.carevoice.agent.PlannedQuestion;
import com.carevoice.agent.QuestionPlannerAgent;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
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

class DirectedPainAnswerTest {
    private static final String PAIN_QUESTION = "On a scale from 0 to 10, how would you rate your pain today?";
    private static final String GROQ_TRANSCRIPT = "The pain was at 0 to 10 at 5, the pain was at 5.";
    private static final String MEDICATION_QUESTION = "Have you taken your prescribed medication today?";

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "MEDICATION_TAKEN, Yes I did take my medication today.",
            "APPETITE, I haven't really felt hungry today.",
            "SLEEP_QUALITY, I keep waking up in the middle of the night."
    })
    void naturalAnswersAdvanceAndHistoryPreservesOriginalText(MonitoringField field, String answer) {
        Patient patient = new Patient("Test", "Daily monitoring");
        HistoryTestSupport.setId(patient, 4L);
        MonitoringSession session = new MonitoringSession(patient);
        HistoryTestSupport.setId(session, 8L);
        session.setPainScore(2);
        session.setRequestedField(field);
        session.setNextQuestion("Current question");
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(8L)).thenReturn(Optional.of(session));
        when(sessions.findByIdForUpdate(8L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        HistoryTestSupport history = HistoryTestSupport.capture(sessions, agent(sessions), null);
        String original = "  " + answer + "  \n";

        var response = history.responses.acceptTranscript(8L, original);

        assertThat(response.missingFields()).doesNotContain(field);
        assertThat(response.requestedField()).isNotEqualTo(field);
        assertThat(history.turns.get(0).getPatientResponse()).isEqualTo(original);
        switch (field) {
            case MEDICATION_TAKEN -> assertThat(session.getMedicationTaken()).isTrue();
            case APPETITE -> assertThat(session.getAppetite()).isEqualTo("reduced");
            case SLEEP_QUALITY -> assertThat(session.getSleepQuality()).isEqualTo("poor");
            default -> throw new AssertionError(field);
        }
    }

    @Test
    void medicationAcknowledgmentRemainsMissing() {
        MonitoringSession session = preparedSession();
        session.setPainScore(2);
        session.setRequestedField(MonitoringField.MEDICATION_TAKEN);
        session.setNextQuestion(MEDICATION_QUESTION);
        var response = agent(session, (planned, context) -> planned.question()).processMessage(1L, "Okay.");
        assertThat(session.getMedicationTaken()).isNull();
        assertThat(response.missingFields()).contains(MonitoringField.MEDICATION_TAKEN);
    }

    @Test
    void capturedPainScoreAdvancesPastThePainQuestion() {
        MonitoringSession session = preparedSession();
        QuestionWordingService wording = mock(QuestionWordingService.class);
        when(wording.generateQuestion(any(), any())).thenAnswer(invocation -> {
            PlannedQuestion planned = invocation.getArgument(0);
            return planned.question();
        });

        var response = agent(session, wording).processMessage(1L, GROQ_TRANSCRIPT);

        assertThat(session.getPainScore()).isEqualTo(5);
        assertThat(session.getRiskLevel()).isEqualTo(RiskLevel.GREEN);
        assertThat(session.getEscalationReason()).isNull();
        assertThat(response.requestedField()).isEqualTo(MonitoringField.MEDICATION_TAKEN);
        assertThat(response.nextQuestion()).isEqualTo(MEDICATION_QUESTION);
        assertThat(response.missingFields()).doesNotContain(MonitoringField.PAIN_SCORE);
        ArgumentCaptor<PlannedQuestion> planned = ArgumentCaptor.forClass(PlannedQuestion.class);
        verify(wording).generateQuestion(planned.capture(), any());
        assertThat(planned.getValue()).isEqualTo(new PlannedQuestion(
                MonitoringField.MEDICATION_TAKEN,
                MEDICATION_QUESTION));
    }

    @Test
    void painAtTenStillUsesTheExistingEscalationRule() {
        MonitoringSession session = preparedSession();
        var response = agent(session, (planned, context) -> planned.question())
                .processMessage(1L, "10");

        assertThat(session.getPainScore()).isEqualTo(10);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.YELLOW);
        assertThat(session.getEscalationReason())
                .isEqualTo("Pain score is at or above the configured review threshold (7/10).");
        assertThat(response.requestedField()).isEqualTo(MonitoringField.MEDICATION_TAKEN);
    }

    @Test
    void historyKeepsTheOriginalPainTranscript() {
        Patient patient = new Patient("Daily Check-In", "Daily monitoring");
        HistoryTestSupport.setId(patient, 4L);
        MonitoringSession session = new MonitoringSession(patient);
        HistoryTestSupport.setId(session, 8L);
        session.setRequestedField(MonitoringField.PAIN_SCORE);
        session.setNextQuestion(PAIN_QUESTION);
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(8L)).thenReturn(Optional.of(session));
        when(sessions.findByIdForUpdate(8L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        HistoryTestSupport history = HistoryTestSupport.capture(sessions, agent(sessions), null);

        history.responses.acceptTranscript(8L, GROQ_TRANSCRIPT);

        assertThat(history.turns).hasSize(1);
        assertThat(history.turns.get(0).getQuestion()).isEqualTo(PAIN_QUESTION);
        assertThat(history.turns.get(0).getPatientResponse()).isEqualTo(GROQ_TRANSCRIPT);
        assertThat(session.getPainScore()).isEqualTo(5);
        assertThat(session.getNextQuestion()).isEqualTo(MEDICATION_QUESTION);
    }

    @Test
    void unclearPainAnswerAsksForANumberInsteadOfRepeatingTheQuestion() {
        MonitoringSession session = preparedSession();
        QuestionWordingService wording = mock(QuestionWordingService.class);
        when(wording.generateQuestion(any(), any())).thenReturn("How is your breathing today?");
        ClinicalMonitoringAgent agent = agent(session, wording);

        var clarification = agent.processMessage(1L, "My pain isn't fine.");

        assertThat(session.getPainScore()).isNull();
        assertThat(clarification.requestedField()).isEqualTo(MonitoringField.PAIN_SCORE);
        assertThat(clarification.nextQuestion()).isEqualTo(QuestionClarification.PAIN_SCORE);
        assertThat(clarification.nextQuestion()).isNotEqualTo(PAIN_QUESTION);
        verify(wording, never()).generateQuestion(any(), any());

        var next = agent.processMessage(1L, "Five.");

        assertThat(session.getPainScore()).isEqualTo(5);
        assertThat(next.requestedField()).isEqualTo(MonitoringField.MEDICATION_TAKEN);
        assertThat(next.nextQuestion()).isEqualTo("How is your breathing today?");
        ArgumentCaptor<PlannedQuestion> planned = ArgumentCaptor.forClass(PlannedQuestion.class);
        verify(wording).generateQuestion(planned.capture(), any());
        assertThat(planned.getValue().field()).isEqualTo(MonitoringField.MEDICATION_TAKEN);
        assertThat(planned.getValue().question()).isEqualTo(MEDICATION_QUESTION);
    }

    @Test
    void aSecondUnusablePainAnswerDoesNotRepeatTheClarification() {
        MonitoringSession session = preparedSession();
        ClinicalMonitoringAgent agent = agent(session, (planned, context) -> planned.question());

        agent.processMessage(1L, "My pain isn't fine.");
        var next = agent.processMessage(1L, "My pain isn't fine.");

        assertThat(session.getPainScore()).isNull();
        assertThat(next.requestedField()).isEqualTo(MonitoringField.MEDICATION_TAKEN);
        assertThat(next.nextQuestion()).isEqualTo(MEDICATION_QUESTION);
        assertThat(next.nextQuestion()).isNotEqualTo(QuestionClarification.PAIN_SCORE);
        assertThat(next.nextQuestion()).isNotEqualTo(PAIN_QUESTION);
    }

    @Test
    void historyKeepsAnUnparsedPainTranscript() {
        Patient patient = new Patient("Daily Check-In", "Daily monitoring");
        HistoryTestSupport.setId(patient, 4L);
        MonitoringSession session = new MonitoringSession(patient);
        HistoryTestSupport.setId(session, 9L);
        session.setRequestedField(MonitoringField.PAIN_SCORE);
        session.setNextQuestion(PAIN_QUESTION);
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        when(sessions.findByIdForUpdate(9L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        HistoryTestSupport history = HistoryTestSupport.capture(sessions, agent(sessions), null);
        String transcript = "My pain isn't fine.";

        history.responses.acceptTranscript(9L, transcript);

        assertThat(history.turns.get(0).getPatientResponse()).isEqualTo(transcript);
        assertThat(session.getPainScore()).isNull();
        assertThat(session.getNextQuestion()).isEqualTo(QuestionClarification.PAIN_SCORE);
        assertThat(history.noteFor(1).orElseThrow().getExtractedFactsJson()).doesNotContain("painScore");
    }

    private static MonitoringSession preparedSession() {
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Daily check-in"));
        session.setRequestedField(MonitoringField.PAIN_SCORE);
        session.setNextQuestion(PAIN_QUESTION);
        return session;
    }

    private static ClinicalMonitoringAgent agent(MonitoringSession session, QuestionWordingService wording) {
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(any())).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return agent(sessions, wording);
    }

    private static ClinicalMonitoringAgent agent(MonitoringSessionRepository sessions) {
        return agent(sessions, (planned, context) -> planned.question());
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
