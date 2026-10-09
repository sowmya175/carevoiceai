package com.carevoice.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Scalar row used to build daily status without loading session graphs. */
public record DailySessionRow(
        Long patientId,
        Long sessionId,
        LocalDate checkInDate,
        SessionStatus status,
        String monitoringPlanName,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        RiskLevel riskLevel
) {}
