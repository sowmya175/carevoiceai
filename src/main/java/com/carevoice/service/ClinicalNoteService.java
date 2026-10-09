package com.carevoice.service;

public interface ClinicalNoteService {
    String write(String question, String patientResponse, ExtractedClinicalFacts facts);

    String provider();
}
