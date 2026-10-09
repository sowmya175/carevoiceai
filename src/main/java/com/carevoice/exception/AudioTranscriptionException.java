package com.carevoice.exception;

public class AudioTranscriptionException extends RuntimeException {
    public static final String CLIENT_MESSAGE =
            "Unable to transcribe this recording. Please try again or use text input.";

    public AudioTranscriptionException() {
        super(CLIENT_MESSAGE);
    }
}
