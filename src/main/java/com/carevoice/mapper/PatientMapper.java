package com.carevoice.mapper;

import com.carevoice.domain.Patient;
import com.carevoice.dto.patient.PatientResponse;

public final class PatientMapper {
    private PatientMapper() {}

    public static PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getDisplayName(),
                patient.getMonitoringPlan(),
                patient.getFullName(),
                patient.getMedicalCondition(),
                patient.getTimezone(),
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }
}
