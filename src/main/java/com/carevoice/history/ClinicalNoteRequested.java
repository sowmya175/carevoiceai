package com.carevoice.history;

import com.carevoice.agent.ExtractedClinicalFacts;

public record ClinicalNoteRequested(
        Long noteId,
        Long sessionId,
        String question,
        String patientResponse,
        ExtractedClinicalFacts extractedFacts
) {}
