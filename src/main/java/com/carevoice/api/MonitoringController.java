package com.carevoice.api;

import org.springframework.security.access.prepost.PreAuthorize;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.domain.InputMode;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.history.MonitoringResponseService;
import com.carevoice.service.MonitoringAgentService;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    private final MonitoringAgentService sessions;
    private final MonitoringResponseService responses;

    public MonitoringController(MonitoringAgentService sessions, MonitoringResponseService responses) {
        this.sessions = sessions;
        this.responses = responses;
    }

    @PostMapping("/patients/{patientId}/sessions")
    @PreAuthorize("@patientAccess.ownsPatient(#patientId, authentication)")
    public SessionResponse start(@PathVariable Long patientId) {
        return SessionResponse.from(sessions.startSession(patientId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("@patientAccess.ownsSession(#sessionId, authentication)")
    public ClinicalAgentResponse message(@PathVariable Long sessionId,
                                         @Valid @RequestBody PatientMessageRequest request) {
        if (request.inputMode() == InputMode.VOICE) {
            return responses.acceptTranscript(sessionId, request.message());
        }
        return responses.acceptText(sessionId, request.message());
    }

    public record PatientMessageRequest(
            @NotBlank @JsonAlias("transcript") String message,
            InputMode inputMode
    ) {}

    public record SessionResponse(
            Long sessionId,
            String status,
            String riskLevel,
            Integer painScore,
            Boolean dizziness,
            String dizzinessOnset,
            Boolean lossOfConsciousness,
            Boolean medicationTaken,
            Boolean appetiteReduced,
            String appetite,
            String sleepQuality,
            Boolean shortnessOfBreath,
            Double temperature,
            String nextQuestion,
            String escalationReason
    ) {
        static SessionResponse from(MonitoringSession s) {
            return new SessionResponse(
                    s.getId(),
                    s.getStatus().name(),
                    s.getRiskLevel().name(),
                    s.getPainScore(),
                    s.getDizziness(),
                    s.getDizzinessOnset(),
                    s.getLossOfConsciousness(),
                    s.getMedicationTaken(),
                    s.getAppetiteReduced(),
                    s.getAppetite(),
                    s.getSleepQuality(),
                    s.getShortnessOfBreath(),
                    s.getTemperature(),
                    s.getNextQuestion(),
                    s.getEscalationReason()
            );
        }
    }
}
