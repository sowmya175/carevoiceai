package com.carevoice.history;

import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record PatientHistoryResponse(
        Long patientId,
        List<SessionSummary> sessions
) {
    public record SessionSummary(
            Long sessionId,
            OffsetDateTime startedAt,
            SessionStatus status,
            RiskLevel riskLevel,
            long turnCount
    ) {}
}
