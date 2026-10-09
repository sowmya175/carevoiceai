package com.carevoice.voice;

import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.config.GroqVoiceCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.Set;

@Component
@Conditional(GroqVoiceCondition.class)
public class GroqWhisperAudioTranscriptionService implements AudioTranscriptionService {
    private static final Logger log = LoggerFactory.getLogger(GroqWhisperAudioTranscriptionService.class);
    private static final Set<String> EXTENSIONS = Set.of(
            "flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm");

    private final RestClient groq;
    private final CareVoiceVoiceProperties properties;

    public GroqWhisperAudioTranscriptionService(RestClient groq, CareVoiceVoiceProperties properties) {
        this.groq = groq;
        this.properties = properties;
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String originalFilename, String contentType) {
        return transcribe(audio, originalFilename, contentType, null);
    }

    @Override
    public TranscriptionResult transcribe(
            byte[] audio, String originalFilename, String contentType, String prompt) {
        try {
            GroqTranscriptionResponse response = groq.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody(audio, originalFilename, contentType, prompt))
                    .retrieve()
                    .body(GroqTranscriptionResponse.class);
            String text = response == null ? null : response.text();
            return new TranscriptionResult(text, properties.getGroq().getModel());
        } catch (AudioTranscriptionException ex) {
            throw ex;
        } catch (RestClientException ex) {
            logFailure(ex);
            throw new AudioTranscriptionException();
        }
    }

    MultiValueMap<String, Object> multipartBody(byte[] audio, String originalFilename, String contentType) {
        return multipartBody(audio, originalFilename, contentType, null);
    }

    MultiValueMap<String, Object> multipartBody(
            byte[] audio, String originalFilename, String contentType, String prompt) {
        String filename = uploadFilename(originalFilename, contentType);
        ByteArrayResource file = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(file, fileHeaders));
        body.add("model", properties.getGroq().getModel());
        String effectivePrompt = prompt == null || prompt.isBlank()
                ? properties.getTranscriptionPrompt()
                : prompt;
        addIfPresent(body, "prompt", effectivePrompt);
        addIfPresent(body, "language", properties.getLanguage());
        body.add("temperature", "0");
        return body;
    }

    private static void addIfPresent(MultiValueMap<String, Object> body, String name, String value) {
        if (value != null && !value.isBlank()) {
            body.add(name, value.trim());
        }
    }

    static String uploadFilename(String originalFilename, String contentType) {
        String basename = basename(originalFilename);
        if (basename != null && EXTENSIONS.contains(extension(basename))) {
            return basename;
        }
        return "audio" + extensionFor(contentType);
    }

    private static void logFailure(RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            log.warn("Groq voice transcription failed errorType={} status={}",
                    ex.getClass().getSimpleName(), response.getStatusCode().value());
            return;
        }
        log.warn("Groq voice transcription failed errorType={}", ex.getClass().getSimpleName());
    }

    private static String basename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String normalized = originalFilename.replace('\\', '/').trim();
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (name.isBlank() || !name.matches("[A-Za-z0-9._-]{1,120}")) {
            return null;
        }
        return name;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "audio/flac" -> ".flac";
            case "audio/mpeg" -> ".mp3";
            case "audio/mp4" -> ".mp4";
            case "audio/m4a" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/wav" -> ".wav";
            case "audio/webm" -> ".webm";
            default -> ".webm";
        };
    }
}
