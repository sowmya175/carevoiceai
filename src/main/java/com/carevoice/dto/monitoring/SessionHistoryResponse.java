package com.carevoice.dto.monitoring;

import com.carevoice.domain.InputMode;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record SessionHistoryResponse(
        Long sessionId,
        Long patientId,
        OffsetDateTime startedAt,
        SessionStatus status,
        String currentQuestion,
        boolean conversationComplete,
        String monitoringPlanName,
        LocalDate checkInDate,
        List<Turn> turns
) {
    public record Turn(
            int sequenceNumber,
            Instant timestamp,
            String question,
            String patientResponse,
            InputMode inputMode,
            String clinicalNote,
            Map<String, Object> extractedFacts,
            RiskLevel riskLevel
    ) {}
}
