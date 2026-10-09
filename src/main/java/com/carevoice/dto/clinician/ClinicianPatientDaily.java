package com.carevoice.dto.clinician;
import com.carevoice.domain.DailyCheckInStatus;

import com.carevoice.domain.RiskLevel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Read model for the clinician dashboard. Nothing here is stored separately.
 * {@code requiresReview} is true only when {@code todayStatus} is {@link DailyCheckInStatus#READY_FOR_REVIEW}.
 * That status is the existing terminal state: a red monitoring flag ends the check-in for review, and a yellow
 * flag does so when the question plan is finished. A green flag completes the check-in. This is not a new rule.
 * {@code latestMonitoringFlag} is the risk level already stored on the session that represents today's check-in.
 * It is absent when today has not started.
 */
public record ClinicianPatientDaily(
        Long patientId,
        String fullName,
        String medicalCondition,
        String timezone,
        String monitoringPlanName,
        LocalDate todayCheckInDate,
        DailyCheckInStatus todayStatus,
        Long todaySessionId,
        RiskLevel latestMonitoringFlag,
        OffsetDateTime latestCheckInAt,
        boolean requiresReview
) {}
