package com.carevoice.dto.patient;

import jakarta.validation.constraints.NotBlank;

public record CreatePatientRequest(
        @NotBlank String displayName,
        @NotBlank String monitoringPlan
) {}
