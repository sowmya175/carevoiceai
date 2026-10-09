package com.carevoice.exception;

/** Generation failed before a proposal was stored. The message is for logs, not the clinician. */
public class PlanGenerationFailedException extends RuntimeException {
    public PlanGenerationFailedException() {
        super("plan generation failed");
    }
}
