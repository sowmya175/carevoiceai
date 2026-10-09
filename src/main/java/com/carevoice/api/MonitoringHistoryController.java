package com.carevoice.api;

import org.springframework.security.access.prepost.PreAuthorize;

import com.carevoice.history.MonitoringHistoryQuery;
import com.carevoice.history.PatientHistoryResponse;
import com.carevoice.history.SessionHistoryResponse;
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
