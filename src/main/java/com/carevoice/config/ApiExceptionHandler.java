package com.carevoice.config;

import com.carevoice.plan.MonitoringPlanUnavailableException;
import com.carevoice.voice.AudioTranscriptionException;
import com.carevoice.voice.BlankTranscriptionException;
import com.carevoice.voice.VoiceUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final CareVoiceVoiceProperties voiceProperties;

    public ApiExceptionHandler(CareVoiceVoiceProperties voiceProperties) {
        this.voiceProperties = voiceProperties;
    }

    @ExceptionHandler(MonitoringPlanUnavailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleMissingPlan(MonitoringPlanUnavailableException ex) {
        return Map.of("error", MonitoringPlanUnavailableException.CLIENT_MESSAGE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(VoiceUploadException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleVoiceUpload(VoiceUploadException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleVoiceTooLarge(MaxUploadSizeExceededException ex) {
        return Map.of("error", VoiceUploadException.tooLarge(voiceProperties.getMaxFileSizeMb()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleMissingAudio(MissingServletRequestPartException ex) {
        return Map.of("error", VoiceUploadException.REQUIRED);
    }

    @ExceptionHandler(BlankTranscriptionException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public Map<String, String> handleBlankTranscript(BlankTranscriptionException ex) {
        return Map.of("error", BlankTranscriptionException.CLIENT_MESSAGE);
    }

    @ExceptionHandler(AudioTranscriptionException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleTranscriptionFailure(AudioTranscriptionException ex) {
        return Map.of("error", AudioTranscriptionException.CLIENT_MESSAGE);
    }
}
