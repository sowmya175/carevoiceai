package com.carevoice.service;

public record ClinicalNoteRequested(
        Long noteId,
        Long sessionId,
        String question,
        String patientResponse,
        ExtractedClinicalFacts extractedFacts
) {}
