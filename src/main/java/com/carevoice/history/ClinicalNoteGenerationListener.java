package com.carevoice.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ClinicalNoteGenerationListener {
    private static final Logger log = LoggerFactory.getLogger(ClinicalNoteGenerationListener.class);

    private final ClinicalNoteAttacher notes;

    public ClinicalNoteGenerationListener(ClinicalNoteAttacher notes) {
        this.notes = notes;
    }

    @Async("clinicalNoteExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generateNote(ClinicalNoteRequested request) {
        try {
            notes.attach(request);
        } catch (RuntimeException ex) {
            log.warn("Asynchronous clinical note generation failed sessionId={} errorType={}",
                    request.sessionId(), ex.getClass().getSimpleName());
        }
    }
}
