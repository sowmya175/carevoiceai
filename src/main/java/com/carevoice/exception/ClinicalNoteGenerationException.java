package com.carevoice.exception;

public class ClinicalNoteGenerationException extends RuntimeException {
    public ClinicalNoteGenerationException() {
        super("Clinical note generation failed");
    }
}
