package com.carevoice.voice;

import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.config.GroqConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "CAREVOICE_RUN_GROQ_AUDIO_TESTS", matches = "true")
class GroqWhisperAudioTranscriptionIntegrationTest {

    @Test
    void transcribesALocalSampleWithoutPrintingTheApiKey() throws Exception {
        String apiKey = System.getenv("GROQ_API_KEY");
        String audioPath = System.getenv("CAREVOICE_TEST_AUDIO_PATH");
        assumeTrue(apiKey != null && !apiKey.isBlank());
        assumeTrue(audioPath != null && Files.isRegularFile(Path.of(audioPath)));

        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        properties.setEnabled(true);
        properties.setProvider("groq");
        properties.getGroq().setApiKey(apiKey);
        properties.getGroq().setBaseUrl(System.getenv().getOrDefault(
                "GROQ_BASE_URL", "https://api.groq.com/openai/v1"));
        properties.getGroq().setModel(System.getenv().getOrDefault(
                "GROQ_WHISPER_MODEL", "whisper-large-v3-turbo"));
        String contentType = System.getenv().getOrDefault("CAREVOICE_TEST_AUDIO_CONTENT_TYPE", "audio/wav");
        byte[] audio = Files.readAllBytes(Path.of(audioPath));

        RestClient client = GroqConfiguration.restClientBuilder(
                properties.getGroq().getBaseUrl(), apiKey).build();
        GroqWhisperAudioTranscriptionService service = new GroqWhisperAudioTranscriptionService(client, properties);
        TranscriptionResult result = service.transcribe(audio, "sample", contentType);
        assertThat(result.model()).isEqualTo(properties.getGroq().getModel());
        assertThat(result.transcript()).isNotBlank();
    }
}
