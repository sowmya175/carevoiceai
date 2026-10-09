package com.carevoice.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class SupportedAudioTypes {
    private static final Map<String, String> CANONICAL = Map.ofEntries(
            Map.entry("audio/flac", "audio/flac"),
            Map.entry("audio/x-flac", "audio/flac"),
            Map.entry("audio/mp3", "audio/mpeg"),
            Map.entry("audio/mpeg", "audio/mpeg"),
            Map.entry("audio/mpga", "audio/mpeg"),
            Map.entry("audio/mp4", "audio/mp4"),
            Map.entry("audio/m4a", "audio/m4a"),
            Map.entry("audio/x-m4a", "audio/m4a"),
            Map.entry("audio/ogg", "audio/ogg"),
            Map.entry("audio/wav", "audio/wav"),
            Map.entry("audio/x-wav", "audio/wav"),
            Map.entry("audio/wave", "audio/wav"),
            Map.entry("audio/vnd.wave", "audio/wav"),
            Map.entry("audio/webm", "audio/webm"));

    private SupportedAudioTypes() {}

    static Optional<String> canonical(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return Optional.empty();
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator).trim();
        }
        return Optional.ofNullable(CANONICAL.get(normalized));
    }
}
