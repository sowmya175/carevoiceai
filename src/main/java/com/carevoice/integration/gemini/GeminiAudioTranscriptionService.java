package com.carevoice.integration.gemini;
import com.carevoice.exception.AudioTranscriptionException;
import com.carevoice.service.AudioTranscriptionService;
import com.carevoice.service.TranscriptionResult;

import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.config.GeminiVoiceCondition;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.gaos.models.interactions.AudioContent;
import com.google.genai.gaos.models.interactions.AudioContentMimeType;
import com.google.genai.gaos.models.interactions.Content;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.GenerationConfig;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionStatus;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.Step;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.interactions.TranscriptionConfig;
import com.google.genai.gaos.models.interactions.TranscriptionConfigMode;
import com.google.genai.gaos.models.interactions.VerbatimTranscriptionMode;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;
import com.google.genai.gaos.models.operations.CreateInteractionResponse;
import com.google.genai.types.DeleteFileConfig;
import com.google.genai.types.FileState;
import com.google.genai.types.GetFileConfig;
import com.google.genai.types.UploadFileConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(GeminiVoiceCondition.class)
public class GeminiAudioTranscriptionService implements AudioTranscriptionService {
    private static final Logger log = LoggerFactory.getLogger(GeminiAudioTranscriptionService.class);
    private static final int MAX_FILE_POLLS = 40;
    private static final long POLL_INTERVAL_MS = 500;

    private final Client client;
    private final CareVoiceVoiceProperties properties;

    public GeminiAudioTranscriptionService(Client client, CareVoiceVoiceProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String originalFilename, String contentType) {
        String remoteName = null;
        try {
            String geminiType = toGeminiMime(contentType);
            UploadedAudio uploaded = uploadAudio(audio, geminiType);
            remoteName = uploaded.name();
            String transcript = requestTranscript(uploaded, geminiType);
            return new TranscriptionResult(transcript, properties.getGeminiTranscriptionModel());
        } catch (AudioTranscriptionException ex) {
            throw ex;
        } catch (ApiException | GenAiIOException ex) {
            throw new AudioTranscriptionException();
        } finally {
            deleteRemoteFile(remoteName);
        }
    }

    public UploadedAudio uploadAudio(byte[] audio, String contentType) {
        com.google.genai.types.File uploaded = client.files.upload(audio, uploadConfig(contentType));
        String name = uploaded.name().orElse(null);
        try {
            com.google.genai.types.File ready = awaitActive(name, uploaded);
            String uri = ready.uri().filter(value -> !value.isBlank()).orElse(null);
            if (uri == null) {
                throw new AudioTranscriptionException();
            }
            return new UploadedAudio(name, uri);
        } catch (AudioTranscriptionException | ApiException | GenAiIOException ex) {
            deleteRemoteFile(name);
            throw ex;
        }
    }

    public String requestTranscript(UploadedAudio uploaded, String contentType) {
        CreateModelInteraction request = transcriptionRequest(
                uploaded.uri(), contentType, properties.getGeminiTranscriptionModel());
        CreateInteractionResponse response = client.interactions.create(CreateInteractionRequestBody.of(request));
        Interaction interaction = response.interaction().orElseThrow(AudioTranscriptionException::new);
        return readTranscript(interaction);
    }

    public void deleteRemoteFile(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            client.files.delete(name, DeleteFileConfig.builder().build());
        } catch (RuntimeException ex) {
            log.warn("Gemini voice file cleanup failed errorType={}", ex.getClass().getSimpleName());
        }
    }

    static String toGeminiMime(String contentType) {
        if ("audio/mp4".equals(contentType)) {
            return "audio/m4a";
        }
        return contentType;
    }

    public static CreateModelInteraction transcriptionRequest(String uri, String mimeType, String model) {
        AudioContent audio = AudioContent.builder()
                .uri(uri)
                .mimeType(AudioContentMimeType.of(mimeType))
                .build();
        TranscriptionConfig transcriptionConfig = TranscriptionConfig.builder()
                .mode(TranscriptionConfigMode.of(VerbatimTranscriptionMode.builder().build()))
                .build();
        return CreateModelInteraction.builder()
                .model(model)
                .input(InteractionsInput.of(audio))
                .generationConfig(GenerationConfig.builder().transcriptionConfig(transcriptionConfig).build())
                .store(false)
                .build();
    }

    private UploadFileConfig uploadConfig(String contentType) {
        return UploadFileConfig.builder().mimeType(contentType).build();
    }

    private com.google.genai.types.File awaitActive(String name, com.google.genai.types.File current) {
        for (int attempt = 0; attempt < MAX_FILE_POLLS; attempt++) {
            FileState.Known state = stateOf(current);
            if (state == FileState.Known.ACTIVE) {
                return current;
            }
            if (state == FileState.Known.FAILED) {
                throw new AudioTranscriptionException();
            }
            if (name == null || name.isBlank()) {
                throw new AudioTranscriptionException();
            }
            pause();
            current = client.files.get(name, GetFileConfig.builder().build());
        }
        throw new AudioTranscriptionException();
    }

    private FileState.Known stateOf(com.google.genai.types.File file) {
        if (file.state().isEmpty()) {
            return FileState.Known.ACTIVE;
        }
        try {
            FileState.Known known = file.state().get().knownEnum();
            if (known == null) {
                throw new AudioTranscriptionException();
            }
            return known;
        } catch (IllegalArgumentException ex) {
            throw new AudioTranscriptionException();
        }
    }

    private void pause() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AudioTranscriptionException();
        }
    }

    private String readTranscript(Interaction interaction) {
        if (interaction.status().isPresent()
                && !InteractionStatus.COMPLETED.equals(interaction.status().get())) {
            throw new AudioTranscriptionException();
        }
        String text = interaction.outputText().orElse(null);
        if (text != null && !text.isBlank()) {
            return text;
        }
        return textFromSteps(interaction);
    }

    private String textFromSteps(Interaction interaction) {
        if (interaction.steps().isEmpty()) {
            return null;
        }
        StringBuilder transcript = new StringBuilder();
        for (Step step : interaction.steps().get()) {
            if (!(step instanceof ModelOutputStep output) || output.content().isEmpty()) {
                continue;
            }
            for (Content content : output.content().get()) {
                if (content instanceof TextContent text && text.text().isPresent()) {
                    if (!transcript.isEmpty()) {
                        transcript.append('\n');
                    }
                    transcript.append(text.text().get());
                }
            }
        }
        return transcript.isEmpty() ? null : transcript.toString();
    }

    public record UploadedAudio(String name, String uri) {}
}
