package com.carevoice.voice;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.agent.CollectedFacts;
import com.carevoice.agent.MissingInformationAnalyzer;
import com.carevoice.agent.QuestionPlannerAgent;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.history.HistoryTestSupport;
import com.carevoice.history.MonitoringResponseService;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.service.ClinicalMonitoringAgent;
import com.carevoice.service.EscalationEngine;
import com.carevoice.service.MonitoringSessionMerger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceMonitoringServiceTest {

    @Test
    void blankTranscriptDoesNotCallTheMonitoringAgent() {
        AudioTranscriptionService transcription = (audio, filename, contentType) ->
                new TranscriptionResult("   \n  ", "gemini-3.5-transcribe");
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        VoiceMonitoringService service = service(transcription, responses, properties(20), sessions());

        assertThatThrownBy(() -> service.process(4L, wav(new byte[]{1, 2, 3})))
                .isInstanceOf(BlankTranscriptionException.class)
                .hasMessage(BlankTranscriptionException.CLIENT_MESSAGE);
        verify(responses, never()).acceptTranscript(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providerFailureDoesNotCallTheMonitoringAgent() {
        AudioTranscriptionService transcription = (audio, filename, contentType) -> {
            throw new AudioTranscriptionException();
        };
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        VoiceMonitoringService service = service(transcription, responses, properties(20), sessions());

        assertThatThrownBy(() -> service.process(4L, wav(new byte[]{1, 2, 3})))
                .isInstanceOf(AudioTranscriptionException.class)
                .hasMessage(AudioTranscriptionException.CLIENT_MESSAGE);
        verify(responses, never()).acceptTranscript(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void voiceTranscriptUsesRuleBasedExtractionThroughTheMonitoringAgent() {
        AudioTranscriptionService transcription = (audio, filename, contentType) ->
                new TranscriptionResult(
                        "  I feel dizzy today and my pain is around six.  ",
                        "gemini-3.5-transcribe");
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Daily check-in"));
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(sessions.findByIdForUpdate(1L)).thenReturn(Optional.of(session));
        when(sessions.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        ClinicalMonitoringAgent agent = new ClinicalMonitoringAgent(
                sessions,
                new RuleBasedClinicalExtractionService(),
                new MonitoringSessionMerger(),
                new EscalationEngine(),
                new MissingInformationAnalyzer(),
                new QuestionPlannerAgent());

        VoiceMonitoringResponse response = service(
                transcription,
                HistoryTestSupport.responses(sessions, agent),
                properties(20),
                sessions)
                .process(1L, wav(new byte[]{1, 2, 3}));

        assertThat(response.transcript()).isEqualTo("I feel dizzy today and my pain is around six.");
        assertThat(response.agentResponse().riskLevel()).isEqualTo(RiskLevel.YELLOW);
        assertThat(response.agentResponse().status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(response.agentResponse().requestedField()).isEqualTo(MonitoringField.DIZZINESS_ONSET);
        assertThat(response.agentResponse().nextQuestion()).isEqualTo("When did the dizziness start?");
        assertThat(response.agentResponse().conversationComplete()).isFalse();
        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
    }

    @Test
    void passesCanonicalMimeTypeAndTrimmedTranscriptToTheAgent() {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        when(transcription.transcribe(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("audio/wav"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TranscriptionResult("  I feel dizzy. ", "gemini-3.5-transcribe"));
        ClinicalAgentResponse agentResponse = new ClinicalAgentResponse(
                8L,
                "When did the dizziness start?",
                MonitoringField.DIZZINESS_ONSET,
                java.util.List.of(MonitoringField.DIZZINESS_ONSET),
                RiskLevel.YELLOW,
                SessionStatus.IN_PROGRESS,
                false,
                CollectedFacts.unknown());
        when(responses.acceptTranscript(8L, "I feel dizzy.")).thenReturn(agentResponse);

        MockMultipartFile audio = new MockMultipartFile("audio", "note.wav", "audio/x-wav", new byte[]{9});
        VoiceMonitoringResponse response = service(transcription, responses, properties(20), sessionWithQuestion(8L))
                .process(8L, audio);

        assertThat(response.transcript()).isEqualTo("I feel dizzy.");
        assertThat(response.agentResponse()).isSameAs(agentResponse);
        verify(responses).acceptTranscript(8L, "I feel dizzy.");
    }

    @Test
    void transcriptionReturnsTextWithoutCreatingATurnOrChangingFacts() {
        MonitoringSession session = new MonitoringSession(new Patient("Ada Lovelace", "Daily monitoring"));
        session.setNextQuestion("On a scale from 0 to 10, how would you rate your pain today?");
        session.setRequestedField(MonitoringField.PAIN_SCORE);
        session.setRiskLevel(RiskLevel.YELLOW);
        session.setEscalationReason("New dizziness/lightheadedness was reported and should be reviewed.");
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        when(transcription.transcribe(any(), any(), eq("audio/wav"), prompt.capture()))
                .thenReturn(new TranscriptionResult("My pain is five.", "whisper-large-v3-turbo"));
        MonitoringResponseService responses = mock(MonitoringResponseService.class);

        String transcript = service(transcription, responses, properties(20), sessions)
                .transcribeOnly(9L, wav(new byte[]{1, 2, 3}));

        assertThat(transcript).isEqualTo("My pain is five.");
        assertThat(session.getPainScore()).isNull();
        assertThat(session.getDizziness()).isNull();
        assertThat(prompt.getValue()).contains("Current question:");
        assertThat(prompt.getValue()).contains(session.getNextQuestion());
        assertThat(prompt.getValue()).contains("Transcribe exactly what the patient says.");
        assertThat(prompt.getValue()).contains("Do not answer the question.");
        assertThat(prompt.getValue()).contains("Do not infer missing words.");
        assertThat(prompt.getValue()).contains("number from zero to ten");
        assertThat(prompt.getValue()).doesNotContain("Ada Lovelace");
        assertThat(prompt.getValue()).doesNotContain("Daily monitoring");
        assertThat(prompt.getValue()).doesNotContain("YELLOW");
        assertThat(prompt.getValue()).doesNotContain("lightheadedness");
        verify(responses, never()).acceptTranscript(any(), any());
        verify(responses, never()).acceptText(any(), any());
        verify(sessions, never()).save(any());
    }

    @Test
    void unsupportedAudioIsRejectedBeforeTranscription() {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        MockMultipartFile audio = new MockMultipartFile("audio", "note.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> service(transcription, responses, properties(20), sessions()).process(3L, audio))
                .isInstanceOf(VoiceUploadException.class)
                .hasMessage(VoiceUploadException.UNSUPPORTED);
        verify(transcription, never()).transcribe(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(responses, never()).acceptTranscript(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static VoiceMonitoringService service(
            AudioTranscriptionService transcription,
            MonitoringResponseService responses,
            CareVoiceVoiceProperties properties,
            MonitoringSessionRepository sessions) {
        return new VoiceMonitoringService(new VoiceUploadValidator(properties), transcription, responses, sessions);
    }

    private static MonitoringSessionRepository sessions() {
        return sessionWithQuestion(4L);
    }

    private static MonitoringSessionRepository sessionWithQuestion(Long sessionId) {
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Daily check-in"));
        session.setNextQuestion("How are you feeling?");
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        return sessions;
    }

    private static CareVoiceVoiceProperties properties(int maxFileSizeMb) {
        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        properties.setMaxFileSizeMb(maxFileSizeMb);
        properties.setProvider("groq");
        return properties;
    }

    private static MockMultipartFile wav(byte[] bytes) {
        return new MockMultipartFile("audio", "note.wav", "audio/wav", bytes);
    }
}
