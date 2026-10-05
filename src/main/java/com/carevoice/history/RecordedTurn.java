package com.carevoice.history;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.agent.ExtractedClinicalFacts;
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
