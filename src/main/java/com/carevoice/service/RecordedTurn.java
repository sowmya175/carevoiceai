package com.carevoice.service;

import com.carevoice.dto.monitoring.ClinicalAgentResponse;

import com.carevoice.domain.InputMode;

public record RecordedTurn(
        Long noteId,
        Long sessionId,
        String question,
        String patientResponse,
        InputMode inputMode,
        ExtractedClinicalFacts extractedFacts,
        ClinicalAgentResponse response
) {}
