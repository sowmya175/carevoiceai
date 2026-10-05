package com.carevoice.history;

import com.carevoice.agent.ExtractedClinicalFacts;
import com.carevoice.agent.MissingInformationAnalyzer;
import com.carevoice.agent.QuestionPlannerAgent;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.carevoice.api.MonitoringHistoryController;
import com.carevoice.config.ApiExceptionHandler;
import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.InputMode;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.MonitoringTurn;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.repository.PatientRepository;
import com.carevoice.service.ClinicalMonitoringAgent;
import com.carevoice.service.EscalationEngine;
import com.carevoice.service.MonitoringSessionMerger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonitoringHistoryTest {
    private static final String OPENING = "Tell me how you are feeling today in your own words.";
    private static final String FIRST_RESPONSE = "I feel dizzy today and my pain is about six.";
    private static final String SECOND_QUESTION = "When did the dizziness start?";
    private static final String SECOND_RESPONSE = "This morning when I stood up.";

    @Test
    void textAndVoiceTurnsKeepThePatientWordsFactsAndNote() {
        Instant before = Instant.now();
        Patient patient = new Patient("Daily Check-In", "Daily monitoring");
        HistoryTestSupport.setId(patient, 4L);
        MonitoringSession session = new MonitoringSession(patient);
        HistoryTestSupport.setId(session, 12L);
        session.setNextQuestion(OPENING);
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findByIdForUpdate(12L)).thenReturn(Optional.of(session));
        when(sessions.findById(12L)).thenReturn(Optional.of(session));
        when(sessions.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        HistoryTestSupport history = HistoryTestSupport.capture(sessions, agent(sessions), null);
        var first = history.responses.acceptText(12L, FIRST_RESPONSE);
        var second = history.responses.acceptTranscript(12L, SECOND_RESPONSE);

        assertThat(history.turns).hasSize(2);
        MonitoringTurn text = history.turns.get(0);
        MonitoringTurn voice = history.turns.get(1);
        assertThat(text.getSequenceNumber()).isEqualTo(1);
        assertThat(text.getInputMode()).isEqualTo(InputMode.TEXT);
        assertThat(text.getQuestion()).isEqualTo(OPENING);
        assertThat(text.getPatientResponse()).isEqualTo(FIRST_RESPONSE);
        assertThat(text.getCreatedAt()).isNotNull();
        assertThat(text.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(text.getCreatedAt()).isBeforeOrEqualTo(Instant.now());

        assertThat(voice.getSequenceNumber()).isEqualTo(2);
        assertThat(voice.getInputMode()).isEqualTo(InputMode.VOICE);
        assertThat(voice.getQuestion()).isEqualTo(SECOND_QUESTION);
        assertThat(voice.getPatientResponse()).isEqualTo(SECOND_RESPONSE);
        assertThat(voice.getCreatedAt()).isAfterOrEqualTo(text.getCreatedAt());

        ClinicalNote firstNote = history.noteFor(1).orElseThrow();
        assertThat(firstNote.getNoteText()).isEqualTo("Patient reports dizziness and pain rated 6/10.");
        assertThat(firstNote.getExtractedFactsJson()).contains("\"painScore\":6").contains("\"dizziness\":true");
        assertThat(firstNote.getExtractedFactsJson()).doesNotContain("lossOfConsciousness");
        assertThat(firstNote.getRiskLevel()).isEqualTo(RiskLevel.YELLOW);
        assertThat(firstNote.getEscalationReason())
                .isEqualTo("New dizziness/lightheadedness was reported and should be reviewed.");
        assertThat(firstNote.getNoteText()).isNotEqualTo(FIRST_RESPONSE);

        assertThat(first.riskLevel()).isEqualTo(RiskLevel.YELLOW);
        assertThat(first.nextQuestion()).isEqualTo(SECOND_QUESTION);
        assertThat(first.requestedField()).isEqualTo(MonitoringField.DIZZINESS_ONSET);
        assertThat(second.nextQuestion()).isEqualTo("Did you faint or lose consciousness?");
        assertThat(second.riskLevel()).isEqualTo(RiskLevel.YELLOW);
        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
        assertThat(session.getDizzinessOnset()).isEqualTo(SECOND_RESPONSE.replaceAll("[.]+$", ""));
    }

    @Test
    void geminiNoteFailureStillPersistsTheTurnAndADeterministicNote() {
        Patient patient = new Patient("Daily Check-In", "Daily monitoring");
        MonitoringSession session = new MonitoringSession(patient);
        HistoryTestSupport.setId(session, 3L);
        session.setNextQuestion(OPENING);
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findByIdForUpdate(3L)).thenReturn(Optional.of(session));
        when(sessions.findById(3L)).thenReturn(Optional.of(session));
        when(sessions.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        GeminiClinicalNoteService gemini = mock(GeminiClinicalNoteService.class);
        when(gemini.write(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("provider unavailable"));
        HistoryTestSupport history = HistoryTestSupport.capture(sessions, agent(sessions), gemini);

        var response = history.responses.acceptText(3L, FIRST_RESPONSE);

        assertThat(history.turns).hasSize(1);
        assertThat(history.turns.get(0).getPatientResponse()).isEqualTo(FIRST_RESPONSE);
        assertThat(history.turns.get(0).getInputMode()).isEqualTo(InputMode.TEXT);
        assertThat(history.noteFor(1).orElseThrow().getNoteText())
                .isEqualTo("Patient reports dizziness and pain rated 6/10.");
        assertThat(history.noteFor(1).orElseThrow().getNoteProvider()).isEqualTo("deterministic");
        assertThat(history.noteFor(1).orElseThrow().getExtractedFactsJson()).contains("\"painScore\":6");
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.YELLOW);
        assertThat(response.nextQuestion()).isEqualTo(SECOND_QUESTION);
    }

    @Test
    void sessionHistoryReturnsTurnsInSequenceOrder() throws Exception {
        Patient patient = new Patient("Daily Check-In", "Daily monitoring");
        HistoryTestSupport.setId(patient, 4L);
        MonitoringSession session = new MonitoringSession(patient);
        HistoryTestSupport.setId(session, 12L);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setRiskLevel(RiskLevel.YELLOW);
        session.setNextQuestion(SECOND_QUESTION);
        setCreatedAt(session, OffsetDateTime.parse("2026-10-02T15:00:00Z"));

        MonitoringTurn first = new MonitoringTurn(patient, session, 1, OPENING, FIRST_RESPONSE, InputMode.VOICE);
        MonitoringTurn second = new MonitoringTurn(patient, session, 2, SECOND_QUESTION, SECOND_RESPONSE, InputMode.TEXT);
        HistoryTestSupport.setId(first, 1L);
        HistoryTestSupport.setId(second, 2L);
        setInstant(second, "createdAt", first.getCreatedAt().minusSeconds(30));
        ClinicalNote firstNote = new ClinicalNote(
                first,
                "{\"painScore\":6,\"dizziness\":true}",
                RiskLevel.YELLOW,
                "New dizziness/lightheadedness was reported and should be reviewed.");
        firstNote.setNoteText("Patient reports dizziness and pain rated 6/10.");
        firstNote.setNoteProvider("gemini");
        firstNote.setModel("gemini-3.8-flash");
        ClinicalNote secondNote = new ClinicalNote(
                second,
                "{\"dizzinessOnset\":\"This morning when I stood up\"}",
                RiskLevel.YELLOW,
                "New dizziness/lightheadedness was reported and should be reviewed.");
        secondNote.setNoteText("Patient reports dizziness onset This morning when I stood up.");

        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        MonitoringTurnRepository turns = mock(MonitoringTurnRepository.class);
        ClinicalNoteRepository notes = mock(ClinicalNoteRepository.class);
        when(sessions.findById(12L)).thenReturn(Optional.of(session));
        when(turns.findByMonitoringSession_IdOrderBySequenceNumberAsc(12L)).thenReturn(List.of(first, second));
        when(notes.findByMonitoringTurn_MonitoringSession_Id(12L)).thenReturn(List.of(secondNote, firstNote));

        mockMvc(new MonitoringHistoryQuery(sessions, turns, notes, mock(PatientRepository.class)))
                .perform(get("/api/monitoring/sessions/12/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(12))
                .andExpect(jsonPath("$.patientId").value(4))
                .andExpect(jsonPath("$.startedAt").value("2026-10-02T15:00:00Z"))
                .andExpect(jsonPath("$.turns[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.turns[1].sequenceNumber").value(2))
                .andExpect(jsonPath("$.turns[0].patientResponse").value(FIRST_RESPONSE))
                .andExpect(jsonPath("$.turns[0].inputMode").value("VOICE"))
                .andExpect(jsonPath("$.turns[0].question").value(OPENING))
                .andExpect(jsonPath("$.turns[0].clinicalNote").value("Patient reports dizziness and pain rated 6/10."))
                .andExpect(jsonPath("$.turns[0].extractedFacts.painScore").value(6))
                .andExpect(jsonPath("$.turns[0].extractedFacts.dizziness").value(true))
                .andExpect(jsonPath("$.turns[0].riskLevel").value("YELLOW"))
                .andExpect(jsonPath("$.turns[0].model").doesNotExist())
                .andExpect(jsonPath("$.turns[0].noteProvider").doesNotExist())
                .andExpect(jsonPath("$.audio").doesNotExist());
    }

    @Test
    void patientHistoryReturnsSessionsNewestFirst() throws Exception {
        Patient patient = new Patient("Daily Check-In", "Daily monitoring");
        HistoryTestSupport.setId(patient, 4L);
        MonitoringSession older = new MonitoringSession(patient);
        MonitoringSession newer = new MonitoringSession(patient);
        HistoryTestSupport.setId(older, 10L);
        HistoryTestSupport.setId(newer, 12L);
        older.setStatus(SessionStatus.COMPLETED);
        older.setRiskLevel(RiskLevel.GREEN);
        newer.setStatus(SessionStatus.IN_PROGRESS);
        newer.setRiskLevel(RiskLevel.YELLOW);
        setCreatedAt(older, OffsetDateTime.parse("2026-10-01T15:00:00Z"));
        setCreatedAt(newer, OffsetDateTime.parse("2026-10-02T15:00:00Z"));

        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        MonitoringTurnRepository turns = mock(MonitoringTurnRepository.class);
        PatientRepository patients = mock(PatientRepository.class);
        when(patients.existsById(4L)).thenReturn(true);
        when(sessions.findByPatient_IdOrderByCreatedAtDescIdDesc(4L)).thenReturn(List.of(newer, older));
        when(turns.countByPatient(4L)).thenReturn(List.<Object[]>of(new Object[]{12L, 2L}, new Object[]{10L, 5L}));

        mockMvc(new MonitoringHistoryQuery(sessions, turns, mock(ClinicalNoteRepository.class), patients))
                .perform(get("/api/patients/4/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(4))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(12))
                .andExpect(jsonPath("$.sessions[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.sessions[0].riskLevel").value("YELLOW"))
                .andExpect(jsonPath("$.sessions[0].turnCount").value(2))
                .andExpect(jsonPath("$.sessions[1].sessionId").value(10))
                .andExpect(jsonPath("$.sessions[1].turnCount").value(5))
                .andExpect(jsonPath("$.sessions[0].patientResponse").doesNotExist());
    }

    @Test
    void rawAudioIsNotPartOfTheHistoryModel() {
        assertThat(MonitoringTurn.class.getDeclaredFields())
                .noneMatch(field -> field.getType().equals(byte[].class) || field.getName().toLowerCase().contains("audio"));
        assertThat(ClinicalNote.class.getDeclaredFields())
                .noneMatch(field -> field.getType().equals(byte[].class) || field.getName().toLowerCase().contains("audio"));
    }

    @Test
    void extractedFactSnapshotOmitsUnmentionedFields() {
        String json = ExtractedFactsJson.write(new ExtractedClinicalFacts(
                6, true, null, null, null, null, null, null, null));
        assertThat(ExtractedFactsJson.read(json)).containsEntry("painScore", 6).containsEntry("dizziness", true);
        assertThat(json).doesNotContain("null");
    }

    private static ClinicalMonitoringAgent agent(MonitoringSessionRepository sessions) {
        return new ClinicalMonitoringAgent(
                sessions,
                new RuleBasedClinicalExtractionService(),
                new MonitoringSessionMerger(),
                new EscalationEngine(),
                new MissingInformationAnalyzer(),
                new QuestionPlannerAgent());
    }

    private static MockMvc mockMvc(MonitoringHistoryQuery query) {
        return MockMvcBuilders.standaloneSetup(new MonitoringHistoryController(query))
                .setControllerAdvice(new ApiExceptionHandler(new CareVoiceVoiceProperties()))
                .build();
    }

    private static void setCreatedAt(MonitoringSession session, OffsetDateTime createdAt) {
        setField(session, "createdAt", createdAt);
    }

    private static void setInstant(Object entity, String fieldName, Instant value) {
        setField(entity, fieldName, value);
    }

    private static void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
