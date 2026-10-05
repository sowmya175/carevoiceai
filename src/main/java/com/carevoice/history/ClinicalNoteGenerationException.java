package com.carevoice.history;

class ClinicalNoteGenerationException extends RuntimeException {
    ClinicalNoteGenerationException() {
        super("Clinical note generation failed");
    }
}
