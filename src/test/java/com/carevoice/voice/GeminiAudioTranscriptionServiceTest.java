package com.carevoice.voice;
import com.carevoice.integration.gemini.GeminiAudioTranscriptionService.UploadedAudio;
import com.carevoice.exception.AudioTranscriptionException;
import com.carevoice.integration.gemini.GeminiAudioTranscriptionService;
import com.carevoice.service.TranscriptionResult;

import com.google.genai.errors.ApiException;
import com.google.genai.gaos.models.interactions.AudioContent;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.VerbatimTranscriptionMode;
import com.carevoice.config.CareVoiceVoiceProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiAudioTranscriptionServiceTest {

    @Test
    void verbatimRequestUsesInteractionsApiWithoutTimestampsOrDiarization() {
        CreateModelInteraction request = GeminiAudioTranscriptionService.transcriptionRequest(
                "https://example.invalid/audio", "audio/wav", "gemini-3.5-transcribe");

        assertThat(request.model().orElseThrow().value()).isEqualTo("gemini-3.5-transcribe");
        assertThat(request.store()).contains(false);
        var mode = request.generationConfig().orElseThrow()
                .transcriptionConfig().orElseThrow()
                .mode().orElseThrow()
                .transcriptionMode().orElseThrow();
        assertThat(mode).isInstanceOf(VerbatimTranscriptionMode.class);
        VerbatimTranscriptionMode verbatim = (VerbatimTranscriptionMode) mode;
        assertThat(verbatim.type()).isEqualTo("verbatim");
        assertThat(verbatim.timestampGranularities()).isEmpty();
        assertThat(verbatim.diarizationMode()).isEmpty();

        AudioContent audio = (AudioContent) request.input().orElseThrow().content().orElseThrow();
        assertThat(audio.uri()).contains("https://example.invalid/audio");
        assertThat(audio.data()).isEmpty();
        assertThat(audio.mimeType().orElseThrow().value()).isEqualTo("audio/wav");
    }

    @Test
    void deletesRemoteFileWhenProviderCallFails() {
        RecordingTranscription service = new RecordingTranscription(true);
        assertThatThrownBy(() -> service.transcribe(new byte[]{1}, "../../patient-note.wav", "audio/wav"))
                .isInstanceOf(AudioTranscriptionException.class)
                .hasMessage(AudioTranscriptionException.CLIENT_MESSAGE)
                .hasMessageNotContaining("secret body");
        assertThat(service.deletedName).isEqualTo("files/unit-test");
    }

    @Test
    void deletesRemoteFileAfterTranscriptIsRead() {
        RecordingTranscription service = new RecordingTranscription(false);
        TranscriptionResult result = service.transcribe(new byte[]{1}, "note.wav", "audio/wav");
        assertThat(result.transcript()).isEqualTo(" I feel dizzy ");
        assertThat(result.model()).isEqualTo("gemini-3.5-transcribe");
        assertThat(service.deletedName).isEqualTo("files/unit-test");
    }

    private static final class RecordingTranscription extends GeminiAudioTranscriptionService {
        private final boolean fail;
        private String deletedName;

        private RecordingTranscription(boolean fail) {
            super(null, properties());
            this.fail = fail;
        }

        @Override
        public UploadedAudio uploadAudio(byte[] audio, String contentType) {
            return new UploadedAudio("files/unit-test", "https://example.invalid/audio");
        }

        @Override
        public String requestTranscript(UploadedAudio uploaded, String contentType) {
            if (fail) {
                throw new ApiException(500, "INTERNAL", "secret body");
            }
            return " I feel dizzy ";
        }

        @Override
        public void deleteRemoteFile(String name) {
            deletedName = name;
        }
    }

    private static CareVoiceVoiceProperties properties() {
        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        properties.setGeminiTranscriptionModel("gemini-3.5-transcribe");
        return properties;
    }
}
