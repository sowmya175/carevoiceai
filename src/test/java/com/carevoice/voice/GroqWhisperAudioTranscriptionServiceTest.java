package com.carevoice.voice;
import com.carevoice.exception.AudioTranscriptionException;
import com.carevoice.exception.BlankTranscriptionException;
import com.carevoice.integration.groq.GroqWhisperAudioTranscriptionService;
import com.carevoice.service.TranscriptionPrompt;
import com.carevoice.service.TranscriptionResult;
import com.carevoice.service.VoiceMonitoringService;
import com.carevoice.exception.VoiceUploadException;
import com.carevoice.service.VoiceUploadValidator;

import com.carevoice.controller.VoiceMonitoringController;
import com.carevoice.exception.ApiExceptionHandler;
import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.config.GroqConfiguration;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.service.MonitoringResponseService;
import com.carevoice.repository.MonitoringSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroqWhisperAudioTranscriptionServiceTest {
    private static final String BASE_URL = "https://groq.test/openai/v1";

    @Test
    void requestUsesConfiguredBaseUrlModelAndMultipartFile() {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3-turbo");
        RecordingClient recording = recordingClient(properties);
        byte[] audio = new byte[]{1, 2, 3, 4};

        recording.server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer unit-test-key"))
                .andExpect(request -> {
                    MockClientHttpRequest mock = (MockClientHttpRequest) request;
                    String contentType = mock.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                    assertThat(contentType).startsWith("multipart/form-data");
                    String body = mock.getBodyAsString();
                    assertThat(body).contains("name=\"file\"");
                    assertThat(body).contains("filename=\"answer.webm\"");
                    assertThat(body).contains("audio/webm");
                    assertThat(body).contains("name=\"model\"");
                    assertThat(body).contains("whisper-large-v3-turbo");
                    assertThat(body).contains("name=\"prompt\"");
                    assertThat(body).contains(CareVoiceVoiceProperties.DEFAULT_TRANSCRIPTION_PROMPT);
                    assertThat(body).contains("name=\"temperature\"");
                    assertThat(body).doesNotContain("name=\"language\"");
                    assertThat(mock.getBodyAsBytes()).containsSequence(audio);
                })
                .andRespond(withSuccess("{\"text\":\"I feel dizzy today\",\"x_groq\":{\"id\":\"hidden\"}}",
                        MediaType.APPLICATION_JSON));

        TranscriptionResult result = recording.service.transcribe(audio, "answer.webm", "audio/webm");

        assertThat(result.transcript()).isEqualTo("I feel dizzy today");
        assertThat(result.model()).isEqualTo("whisper-large-v3-turbo");
        recording.server.verify();
    }

    @Test
    void configuredLanguageAndModelAreSent() {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3");
        properties.setLanguage("en");
        RecordingClient recording = recordingClient(properties);
        recording.server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("name=\"model\"");
                    assertThat(body).contains("whisper-large-v3");
                    assertThat(body).doesNotContain("whisper-large-v3-turbo");
                    assertThat(body).contains("name=\"language\"");
                    assertThat(body).contains("en");
                    assertThat(body).contains("name=\"prompt\"");
                    assertThat(body).contains(CareVoiceVoiceProperties.DEFAULT_TRANSCRIPTION_PROMPT);
                })
                .andRespond(withSuccess("{\"text\":\"five\"}", MediaType.APPLICATION_JSON));

        TranscriptionResult result = recording.service.transcribe(new byte[]{1}, "answer.webm", "audio/webm");

        assertThat(result.model()).isEqualTo("whisper-large-v3");
        assertThat(result.transcript()).isEqualTo("five");
        recording.server.verify();
    }

    @Test
    void contextualPromptIsSentAndPatientContextIsNot() {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3");
        properties.setLanguage("en");
        String prompt = TranscriptionPrompt.forTurn(
                "On a scale from 0 to 10, how would you rate your pain today?",
                MonitoringField.PAIN_SCORE);
        RecordingClient recording = recordingClient(properties);
        recording.server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("whisper-large-v3");
                    assertThat(body).doesNotContain("whisper-large-v3-turbo");
                    assertThat(body).contains("name=\"language\"");
                    assertThat(body).contains("en");
                    assertThat(body).contains("name=\"temperature\"");
                    assertThat(body).contains("0");
                    assertThat(body).contains("Current question:");
                    assertThat(body).contains("On a scale from 0 to 10, how would you rate your pain today?");
                    assertThat(body).contains("Do not answer the question.");
                    assertThat(body).contains("Do not infer missing words.");
                    assertThat(body).contains("number from zero to ten");
                    assertThat(body).doesNotContain("Ada Lovelace");
                    assertThat(body).doesNotContain(CareVoiceVoiceProperties.DEFAULT_TRANSCRIPTION_PROMPT);
                })
                .andRespond(withSuccess("{\"text\":\"five\"}", MediaType.APPLICATION_JSON));

        TranscriptionResult result = recording.service.transcribe(
                new byte[]{1}, "answer.webm", "audio/webm", prompt);

        assertThat(result.transcript()).isEqualTo("five");
        assertThat(result.model()).isEqualTo("whisper-large-v3");
        recording.server.verify();
    }

    @Test
    void unsafeFilenameIsNotUsedAsAPath() {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3-turbo");
        var body = new GroqWhisperAudioTranscriptionService(RestClient.create(), properties)
                .multipartBody(new byte[]{1}, "C:\\secret\\note.bin", "audio/mp4");

        HttpEntityFile file = filePart(body);
        assertThat(file.filename()).isEqualTo("audio.mp4");
        assertThat(file.filename()).doesNotContain("\\");
        assertThat(file.filename()).doesNotContain("secret");
    }

    @Test
    void blankGroqTranscriptUsesTheExisting422Response() throws Exception {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3-turbo");
        RecordingClient recording = recordingClient(properties);
        MonitoringResponseService responses = mock(MonitoringResponseService.class);
        recording.server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withSuccess("{\"text\":\"   \"}", MediaType.APPLICATION_JSON));

        mockMvc(recording.service, responses).perform(multipart("/api/monitoring/sessions/4/voice")
                        .file(audioFile()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value(BlankTranscriptionException.CLIENT_MESSAGE));

        verify(responses, never()).acceptTranscript(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        recording.server.verify();
    }

    @Test
    void groqFailureUsesTheExisting502ResponseWithoutTheProviderBody() throws Exception {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3-turbo");
        RecordingClient recording = recordingClient(properties);
        recording.server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("secret groq transcript and api key"));

        mockMvc(recording.service, mock(MonitoringResponseService.class)).perform(multipart("/api/monitoring/sessions/4/voice")
                        .file(audioFile()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(AudioTranscriptionException.CLIENT_MESSAGE))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));

        recording.server.verify();
    }

    @Test
    void unsupportedAudioIsRejectedBeforeTheGroqCall() throws Exception {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3-turbo");
        RecordingClient recording = recordingClient(properties);

        mockMvc(recording.service, mock(MonitoringResponseService.class)).perform(multipart("/api/monitoring/sessions/4/voice")
                        .file(new MockMultipartFile("audio", "note.txt", "text/plain", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(VoiceUploadException.UNSUPPORTED));

        recording.server.verify();
    }

    private static HttpEntityFile filePart(org.springframework.util.MultiValueMap<String, Object> body) {
        Object part = body.getFirst("file");
        assertThat(part).isInstanceOf(org.springframework.http.HttpEntity.class);
        org.springframework.http.HttpEntity<?> entity = (org.springframework.http.HttpEntity<?>) part;
        assertThat(entity.getBody()).isInstanceOf(org.springframework.core.io.ByteArrayResource.class);
        org.springframework.core.io.ByteArrayResource resource =
                (org.springframework.core.io.ByteArrayResource) entity.getBody();
        return new HttpEntityFile(resource.getFilename());
    }

    private static MockMvc mockMvc(
            GroqWhisperAudioTranscriptionService transcription,
            MonitoringResponseService responses) {
        CareVoiceVoiceProperties properties = properties("whisper-large-v3-turbo");
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Daily monitoring"));
        session.setNextQuestion("How are you feeling?");
        MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
        when(sessions.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(session));
        VoiceMonitoringService service = new VoiceMonitoringService(
                new VoiceUploadValidator(properties),
                transcription,
                responses,
                sessions);
        return MockMvcBuilders.standaloneSetup(new VoiceMonitoringController(service))
                .setControllerAdvice(new ApiExceptionHandler(properties))
                .build();
    }

    private static RecordingClient recordingClient(CareVoiceVoiceProperties properties) {
        RestClient.Builder builder = GroqConfiguration.restClientBuilder(BASE_URL, "unit-test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).bufferContent().build();
        GroqWhisperAudioTranscriptionService service =
                new GroqWhisperAudioTranscriptionService(builder.build(), properties);
        return new RecordingClient(server, service);
    }

    private static CareVoiceVoiceProperties properties(String model) {
        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        properties.setEnabled(true);
        properties.setProvider("groq");
        properties.getGroq().setBaseUrl(BASE_URL);
        properties.getGroq().setApiKey("unit-test-key");
        properties.getGroq().setModel(model);
        return properties;
    }

    private static MockMultipartFile audioFile() {
        return new MockMultipartFile("audio", "answer.webm", "audio/webm", new byte[]{9, 8, 7});
    }

    private record RecordingClient(MockRestServiceServer server, GroqWhisperAudioTranscriptionService service) {}

    private record HttpEntityFile(String filename) {}
}
