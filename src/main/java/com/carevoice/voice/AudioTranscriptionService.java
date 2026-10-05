package com.carevoice.voice;

public interface AudioTranscriptionService {
    TranscriptionResult transcribe(byte[] audio, String originalFilename, String contentType);
}
