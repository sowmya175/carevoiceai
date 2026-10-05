package com.carevoice.api;

import com.carevoice.agent.ClinicalAgentResponse;
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
    public SessionResponse start(@PathVariable Long patientId) {
        return SessionResponse.from(sessions.startSession(patientId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ClinicalAgentResponse message(@PathVariable Long sessionId,
                                         @Valid @RequestBody PatientMessageRequest request) {
        return responses.acceptText(sessionId, request.message());
    }

    public record PatientMessageRequest(
            @NotBlank @JsonAlias("transcript") String message
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
