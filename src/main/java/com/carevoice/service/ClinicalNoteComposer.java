package com.carevoice.service;
import com.carevoice.integration.gemini.GeminiClinicalNoteService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ClinicalNoteComposer {
    private static final Logger log = LoggerFactory.getLogger(ClinicalNoteComposer.class);

    private final ObjectProvider<GeminiClinicalNoteService> geminiNotes;
    private final DeterministicClinicalNoteService deterministicNotes;

    public ClinicalNoteComposer(
            ObjectProvider<GeminiClinicalNoteService> geminiNotes,
            DeterministicClinicalNoteService deterministicNotes) {
        this.geminiNotes = geminiNotes;
        this.deterministicNotes = deterministicNotes;
    }

    public NoteDraft compose(String question, String patientResponse, ExtractedClinicalFacts facts) {
        GeminiClinicalNoteService gemini = geminiNotes.getIfAvailable();
        if (gemini != null) {
            try {
                return new NoteDraft(gemini.write(question, patientResponse, facts), gemini.provider(), gemini.model());
            } catch (RuntimeException ex) {
                log.warn("Gemini clinical note failed; using deterministic note errorType={}",
                        ex.getClass().getSimpleName());
            }
        }
        return new NoteDraft(deterministicNotes.write(question, patientResponse, facts), deterministicNotes.provider(), null);
    }
}
