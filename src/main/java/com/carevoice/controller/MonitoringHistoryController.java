package com.carevoice.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.carevoice.service.MonitoringHistoryQuery;
import com.carevoice.dto.monitoring.PatientHistoryResponse;
import com.carevoice.dto.monitoring.SessionHistoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonitoringHistoryController {
    private final MonitoringHistoryQuery history;

    public MonitoringHistoryController(MonitoringHistoryQuery history) {
        this.history = history;
    }

    @GetMapping("/api/monitoring/sessions/{sessionId}/history")
    @PreAuthorize("@patientAccess.canReadSession(#sessionId, authentication)")
    public SessionHistoryResponse sessionHistory(@PathVariable Long sessionId) {
        return history.sessionHistory(sessionId);
    }

    @GetMapping("/api/patients/{patientId}/history")
    @PreAuthorize("@patientAccess.canReadPatient(#patientId, authentication)")
    public PatientHistoryResponse patientHistory(@PathVariable Long patientId) {
        return history.patientHistory(patientId);
    }
}
