package com.carevoice.service;

public interface AudioTranscriptionService {
    TranscriptionResult transcribe(byte[] audio, String originalFilename, String contentType);

    default TranscriptionResult transcribe(
            byte[] audio, String originalFilename, String contentType, String prompt) {
        return transcribe(audio, originalFilename, contentType);
    }
}
