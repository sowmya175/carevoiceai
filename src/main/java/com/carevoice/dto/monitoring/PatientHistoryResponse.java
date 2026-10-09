package com.carevoice.dto.monitoring;

import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

import java.time.LocalDate;
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
            long turnCount,
            LocalDate checkInDate,
            String monitoringPlanName,
            OffsetDateTime completedAt
    ) {}
}
