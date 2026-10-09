package com.carevoice.longitudinal;

import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Scalar query projection: no transcripts, notes, or lazy entity associations. */
public record SessionFactsSnapshot(
        Long sessionId,
        OffsetDateTime startedAt,
        SessionStatus status,
        RiskLevel riskLevel,
        Integer painScore,
        String sleepQuality,
        String appetite,
        Boolean medicationTaken,
        Boolean dizziness,
        Boolean shortnessOfBreath,
        Boolean lossOfConsciousness,
        String dizzinessOnset,
        Double temperature,
        LocalDate checkInDate,
        String monitoringPlanName
) {}
