package com.carevoice.history;

import com.carevoice.agent.ExtractedClinicalFacts;
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
        attach(turn.noteId(), turn.sessionId(), turn.question(), turn.patientResponse(), turn.extractedFacts());
    }

    public void attach(ClinicalNoteRequested request) {
        attach(request.noteId(), request.sessionId(), request.question(), request.patientResponse(), request.extractedFacts());
    }

    private void attach(
            Long noteId,
            Long sessionId,
            String question,
            String patientResponse,
            ExtractedClinicalFacts facts) {
        NoteDraft draft;
        try {
            draft = composer.compose(question, patientResponse, facts);
        } catch (RuntimeException ex) {
            log.warn("Clinical note generation failed sessionId={} errorType={}",
                    sessionId, ex.getClass().getSimpleName());
            draft = new NoteDraft(
                    deterministicNotes.write(question, patientResponse, facts),
                    deterministicNotes.provider(),
                    null);
        }
        try {
            writer.save(noteId, draft);
        } catch (RuntimeException ex) {
            log.warn("Clinical note save failed sessionId={} errorType={}",
                    sessionId, ex.getClass().getSimpleName());
        }
    }
}
