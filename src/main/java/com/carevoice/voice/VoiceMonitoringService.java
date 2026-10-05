package com.carevoice.voice;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.history.MonitoringResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@ConditionalOnProperty(prefix = "carevoice.voice", name = "enabled", havingValue = "true")
public class VoiceMonitoringService {
    private static final Logger log = LoggerFactory.getLogger(VoiceMonitoringService.class);

    private final VoiceUploadValidator validator;
    private final AudioTranscriptionService transcription;
    private final MonitoringResponseService responses;

    public VoiceMonitoringService(
            VoiceUploadValidator validator,
            AudioTranscriptionService transcription,
            MonitoringResponseService responses) {
        this.validator = validator;
        this.transcription = transcription;
        this.responses = responses;
    }

    public VoiceMonitoringResponse process(Long sessionId, MultipartFile audio) {
        String contentType = validator.validate(audio);
        TranscriptionResult result;
        try {
            result = transcription.transcribe(audio.getBytes(), audio.getOriginalFilename(), contentType);
        } catch (AudioTranscriptionException ex) {
            log.warn("Voice transcription failed sessionId={} errorType={}",
                    sessionId, ex.getClass().getSimpleName());
            throw ex;
        } catch (IOException ex) {
            log.warn("Voice transcription failed sessionId={} errorType={}",
                    sessionId, ex.getClass().getSimpleName());
            throw new AudioTranscriptionException();
        }

        String transcript = result.transcript() == null ? "" : result.transcript().trim();
        if (transcript.isEmpty()) {
            throw new BlankTranscriptionException();
        }
        log.info("Voice transcription completed sessionId={}", sessionId);
        ClinicalAgentResponse agentResponse = responses.acceptTranscript(sessionId, transcript);
        return new VoiceMonitoringResponse(transcript, agentResponse);
    }
}
