package com.carevoice.history;

import com.carevoice.agent.ExtractedClinicalFacts;

public interface ClinicalNoteService {
    String write(String question, String patientResponse, ExtractedClinicalFacts facts);

    String provider();
}
