package com.carevoice.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClinicalNoteAttacher {
    private static final Logger log = LoggerFactory.getLogger(ClinicalNoteAttacher.class);

    private final ClinicalNoteComposer composer;
    private final DeterministicClinicalNoteService deterministicNotes;
    private final ClinicalNoteWriter writer;

    public ClinicalNoteAttacher(
            ClinicalNoteComposer composer,
            DeterministicClinicalNoteService deterministicNotes,
            ClinicalNoteWriter writer) {
        this.composer = composer;
        this.deterministicNotes = deterministicNotes;
        this.writer = writer;
    }

    public void attach(RecordedTurn turn) {
        NoteDraft draft;
        try {
            draft = composer.compose(turn.question(), turn.patientResponse(), turn.extractedFacts());
        } catch (RuntimeException ex) {
            log.warn("Clinical note generation failed sessionId={} errorType={}",
                    turn.sessionId(), ex.getClass().getSimpleName());
            draft = new NoteDraft(
                    deterministicNotes.write(turn.question(), turn.patientResponse(), turn.extractedFacts()),
                    deterministicNotes.provider(),
                    null);
        }
        try {
            writer.save(turn.noteId(), draft);
        } catch (RuntimeException ex) {
            log.warn("Clinical note save failed sessionId={} errorType={}",
                    turn.sessionId(), ex.getClass().getSimpleName());
        }
    }
}
