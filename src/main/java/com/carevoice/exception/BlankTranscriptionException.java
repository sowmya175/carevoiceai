package com.carevoice.exception;

public class BlankTranscriptionException extends RuntimeException {
    public static final String CLIENT_MESSAGE =
            "No understandable speech was detected. Please try recording again or use text input.";

    public BlankTranscriptionException() {
        super(CLIENT_MESSAGE);
    }
}
