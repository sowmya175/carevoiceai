package com.carevoice.controller;

import com.carevoice.dto.monitoring.ClinicalAgentResponse;
import com.carevoice.dto.monitoring.PatientMessageRequest;
import com.carevoice.dto.monitoring.SessionResponse;
import com.carevoice.domain.InputMode;
import com.carevoice.mapper.SessionMapper;
import com.carevoice.service.MonitoringAgentService;
import com.carevoice.service.MonitoringResponseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return SessionMapper.toResponse(sessions.startSession(patientId));
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
}
