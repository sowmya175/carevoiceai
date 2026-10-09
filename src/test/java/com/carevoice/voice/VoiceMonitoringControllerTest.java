package com.carevoice.voice;
import com.carevoice.exception.AudioTranscriptionException;
import com.carevoice.service.AudioTranscriptionService;
import com.carevoice.exception.BlankTranscriptionException;
import com.carevoice.service.TranscriptionResult;
import com.carevoice.service.VoiceMonitoringService;
import com.carevoice.exception.VoiceUploadException;
import com.carevoice.service.VoiceUploadValidator;

import com.carevoice.dto.monitoring.ClinicalAgentResponse;
import com.carevoice.service.CollectedFacts;
import com.carevoice.controller.MonitoringController;
import com.carevoice.controller.VoiceMonitoringController;
import com.carevoice.exception.ApiExceptionHandler;
import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.service.MonitoringResponseService;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.service.MonitoringAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoiceMonitoringControllerTest {

    @Test
    void validUploadReturnsTranscriptAndAgentResponse() throws Exception {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        when(transcription.transcribe(any(), any(), eq("audio/wav"), any()))
                .thenReturn(new TranscriptionResult("I feel dizzy today and my pain is about six.", "gemini-3.5-transcribe"));
        when(responses.acceptTranscript(1L, "I feel dizzy today and my pain is about six."))
                .thenReturn(agentResponse());

        mockMvc(service(transcription, responses, 20)).perform(multipart("/api/monitoring/sessions/1/voice")
                        .file(wav("audio/wav", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("I feel dizzy today and my pain is about six."))
                .andExpect(jsonPath("$.agentResponse.nextQuestion").value("When did the dizziness start?"))
                .andExpect(jsonPath("$.agentResponse.requestedField").value("DIZZINESS_ONSET"))
                .andExpect(jsonPath("$.agentResponse.riskLevel").value("YELLOW"))
                .andExpect(jsonPath("$.agentResponse.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.agentResponse.conversationComplete").value(false));

        verify(transcription).transcribe(any(), any(), eq("audio/wav"), any());
        verify(responses).acceptTranscript(1L, "I feel dizzy today and my pain is about six.");
    }

    @Test
    void emptyUploadReturns400() throws Exception {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        mockMvc(service(transcription, mock(MonitoringResponseService.class), 20))
                .perform(multipart("/api/monitoring/sessions/1/voice").file(wav("audio/wav", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(VoiceUploadException.EMPTY));
        verify(transcription, never()).transcribe(any(), any(), any());
    }

    @Test
    void unsupportedMimeTypeReturns400() throws Exception {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        mockMvc(service(transcription, mock(MonitoringResponseService.class), 20))
                .perform(multipart("/api/monitoring/sessions/1/voice").file(wav("audio/midi", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(VoiceUploadException.UNSUPPORTED));
        verify(transcription, never()).transcribe(any(), any(), any());
    }

    @Test
    void oversizedFileReturns400() throws Exception {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        mockMvc(service(transcription, mock(MonitoringResponseService.class), 0))
                .perform(multipart("/api/monitoring/sessions/1/voice").file(wav("audio/wav", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(VoiceUploadException.tooLarge(0)));
        verify(transcription, never()).transcribe(any(), any(), any());
    }

    @Test
    void blankTranscriptReturnsControlledError() throws Exception {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        when(transcription.transcribe(any(), any(), eq("audio/wav"), any()))
                .thenReturn(new TranscriptionResult("   ", "gemini-3.5-transcribe"));

        mockMvc(service(transcription, responses, 20)).perform(multipart("/api/monitoring/sessions/1/voice")
                        .file(wav("audio/wav", new byte[]{1})))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value(BlankTranscriptionException.CLIENT_MESSAGE));
        verify(responses, never()).acceptTranscript(any(), any());
    }

    @Test
    void providerFailureReturnsControlledError() throws Exception {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        when(transcription.transcribe(any(), any(), any(), any())).thenThrow(new AudioTranscriptionException());

        mockMvc(service(transcription, responses, 20)).perform(multipart("/api/monitoring/sessions/1/voice")
                        .file(wav("audio/wav", new byte[]{1})))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(AudioTranscriptionException.CLIENT_MESSAGE));
        verify(responses, never()).acceptTranscript(any(), any());
    }

    @Test
    void textEndpointStillDelegatesToTheMonitoringAgent() throws Exception {
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        when(responses.acceptText(3L, "I feel dizzy today and my pain is about six."))
                .thenReturn(agentResponse());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MonitoringController(mock(MonitoringAgentService.class), responses)).build();

        mockMvc.perform(post("/api/monitoring/sessions/3/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I feel dizzy today and my pain is about six.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextQuestion").value("When did the dizziness start?"))
                .andExpect(jsonPath("$.riskLevel").value("YELLOW"))
                .andExpect(jsonPath("$.clinicalNote").doesNotExist())
                .andExpect(jsonPath("$.extractedFacts").doesNotExist());
        verify(responses).acceptText(3L, "I feel dizzy today and my pain is about six.");
        verify(responses, never()).acceptTranscript(any(), any());
    }

    @Test
    void transcriptionEndpointReturnsTextWithoutClinicalProcessing() throws Exception {
        AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        when(transcription.transcribe(any(), any(), eq("audio/wav"), any()))
                .thenReturn(new TranscriptionResult("My pain is five.", "whisper-large-v3-turbo"));

        mockMvc(service(transcription, responses, 20)).perform(multipart("/api/monitoring/sessions/1/voice/transcribe")
                        .file(wav("audio/wav", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("My pain is five."))
                .andExpect(jsonPath("$.agentResponse").doesNotExist());

        verify(responses, never()).acceptTranscript(any(), any());
        verify(responses, never()).acceptText(any(), any());
    }

    @Test
    void confirmedVoiceTranscriptUsesTheSharedVoicePath() throws Exception {
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        when(responses.acceptTranscript(3L, "My pain is five.")).thenReturn(agentResponse());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MonitoringController(mock(MonitoringAgentService.class), responses)).build();

        mockMvc.perform(post("/api/monitoring/sessions/3/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"My pain is five.\",\"inputMode\":\"VOICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextQuestion").value("When did the dizziness start?"));

        verify(responses).acceptTranscript(3L, "My pain is five.");
        verify(responses, never()).acceptText(any(), any());
    }

    @Test
    void springMultipartLimitIsReportedAs400() {
        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        var body = new ApiExceptionHandler(properties)
                .handleVoiceTooLarge(new MaxUploadSizeExceededException(properties.maxFileSizeBytes()));
        assertThat(body).containsEntry("error", VoiceUploadException.tooLarge(20));
    }

    private static MockMvc mockMvc(VoiceMonitoringService service) {
        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        return MockMvcBuilders.standaloneSetup(new VoiceMonitoringController(service))
                .setControllerAdvice(new ApiExceptionHandler(properties))
                .build();
    }

    private static VoiceMonitoringService service(
            AudioTranscriptionService transcription,
            MonitoringResponseService responses,
            int maxFileSizeMb) {
        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        properties.setMaxFileSizeMb(maxFileSizeMb);
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Daily monitoring"));
        session.setNextQuestion("Tell me how you are feeling today in your own words.");
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(session));
        return new VoiceMonitoringService(new VoiceUploadValidator(properties), transcription, responses, sessions);
    }

    private static MockMultipartFile wav(String contentType, byte[] bytes) {
        return new MockMultipartFile("audio", "note.wav", contentType, bytes);
    }

    private static ClinicalAgentResponse agentResponse() {
        return new ClinicalAgentResponse(
                1L,
                "When did the dizziness start?",
                MonitoringField.DIZZINESS_ONSET,
                java.util.List.of(MonitoringField.DIZZINESS_ONSET),
                RiskLevel.YELLOW,
                SessionStatus.IN_PROGRESS,
                false,
                CollectedFacts.unknown());
    }
}
