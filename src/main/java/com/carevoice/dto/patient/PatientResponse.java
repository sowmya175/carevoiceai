package com.carevoice.dto.patient;

import java.time.Instant;

/**
 * JSON shape previously produced by serializing {@code Patient} directly.
 * Field names match the entity getters.
 */
public record PatientResponse(
        Long id,
        String displayName,
        String monitoringPlan,
        String fullName,
        String medicalCondition,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {}
