package com.carevoice.voice;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.history.MonitoringResponseService;
import com.carevoice.observability.VoiceTiming;
import com.carevoice.repository.MonitoringSessionRepository;
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
    private final MonitoringSessionRepository sessions;

    public VoiceMonitoringService(
            VoiceUploadValidator validator,
            AudioTranscriptionService transcription,
            MonitoringResponseService responses,
            MonitoringSessionRepository sessions) {
        this.validator = validator;
        this.transcription = transcription;
        this.responses = responses;
        this.sessions = sessions;
    }

    public VoiceMonitoringResponse process(Long sessionId, MultipartFile audio) {
        long started = System.nanoTime();
        try {
            String transcript = transcribe(sessionId, audio);
            ClinicalAgentResponse agentResponse = responses.acceptTranscript(sessionId, transcript);
            return new VoiceMonitoringResponse(transcript, agentResponse);
        } finally {
            VoiceTiming.log(log, "totalMs=" + VoiceTiming.millisSince(started) + " sessionId=" + sessionId);
        }
    }

    public String transcribeOnly(Long sessionId, MultipartFile audio) {
        long started = System.nanoTime();
        try {
            return transcribe(sessionId, audio);
        } finally {
            VoiceTiming.log(log, "totalMs=" + VoiceTiming.millisSince(started) + " sessionId=" + sessionId);
        }
    }

    private String transcribe(Long sessionId, MultipartFile audio) {
        String contentType = validator.validate(audio);
        MonitoringSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring session not found: " + sessionId));
        TranscriptionResult result;
        long transcriptionStarted = System.nanoTime();
        try {
            result = transcription.transcribe(
                    audio.getBytes(),
                    audio.getOriginalFilename(),
                    contentType,
                    TranscriptionPrompt.forSession(session));
        } catch (AudioTranscriptionException ex) {
            VoiceTiming.log(log, "transcriptionMs=" + VoiceTiming.millisSince(transcriptionStarted)
                    + " sessionId=" + sessionId);
            log.warn("Voice transcription failed sessionId={} errorType={}",
                    sessionId, ex.getClass().getSimpleName());
            throw ex;
        } catch (IOException ex) {
            VoiceTiming.log(log, "transcriptionMs=" + VoiceTiming.millisSince(transcriptionStarted)
                    + " sessionId=" + sessionId);
            log.warn("Voice transcription failed sessionId={} errorType={}",
                    sessionId, ex.getClass().getSimpleName());
            throw new AudioTranscriptionException();
        }
        VoiceTiming.log(log, "transcriptionMs=" + VoiceTiming.millisSince(transcriptionStarted)
                + " sessionId=" + sessionId);

        String transcript = result.transcript() == null ? "" : result.transcript().trim();
        if (transcript.isEmpty()) {
            throw new BlankTranscriptionException();
        }
        log.info("Voice transcription completed sessionId={}", sessionId);
        return transcript;
    }
}
