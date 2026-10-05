package com.carevoice.voice;

public class VoiceUploadException extends RuntimeException {
    public static final String REQUIRED = "An audio recording is required.";
    public static final String EMPTY = "The audio recording is empty.";
    public static final String UNSUPPORTED = "Unsupported audio format.";

    public VoiceUploadException(String message) {
        super(message);
    }

    public static String tooLarge(int maxFileSizeMb) {
        return "Recording exceeds the maximum size of " + maxFileSizeMb + " MB.";
    }
}
