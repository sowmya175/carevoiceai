package com.carevoice.voice;

import com.carevoice.config.CareVoiceVoiceProperties;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "CAREVOICE_RUN_GEMINI_AUDIO_TESTS", matches = "true")
class GeminiAudioTranscriptionIntegrationTest {

    @Test
    void transcribesALocalSampleWithoutPrintingTheApiKey() throws Exception {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        String audioPath = System.getenv("CAREVOICE_TEST_AUDIO_PATH");
        assumeTrue(apiKey != null && !apiKey.isBlank());
        assumeTrue(audioPath != null && Files.isRegularFile(Path.of(audioPath)));

        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        properties.setEnabled(true);
        properties.setGeminiTranscriptionModel(
                System.getenv().getOrDefault("GEMINI_TRANSCRIPTION_MODEL", "gemini-3.5-transcribe"));
        String contentType = System.getenv().getOrDefault("CAREVOICE_TEST_AUDIO_CONTENT_TYPE", "audio/wav");
        byte[] audio = Files.readAllBytes(Path.of(audioPath));

        try (Client client = Client.builder().apiKey(apiKey).build()) {
            GeminiAudioTranscriptionService service = new GeminiAudioTranscriptionService(client, properties);
            TranscriptionResult result = service.transcribe(audio, "sample", contentType);
            assertThat(result.model()).isEqualTo(properties.getGeminiTranscriptionModel());
            assertThat(result.transcript()).isNotBlank();
        }
    }
}
