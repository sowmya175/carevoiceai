package com.carevoice.api;

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
    public SessionHistoryResponse sessionHistory(@PathVariable Long sessionId) {
        return history.sessionHistory(sessionId);
    }

    @GetMapping("/api/patients/{patientId}/history")
    public PatientHistoryResponse patientHistory(@PathVariable Long patientId) {
        return history.patientHistory(patientId);
    }
}
