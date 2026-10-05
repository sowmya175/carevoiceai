package com.carevoice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "carevoice.voice", name = "enabled", havingValue = "true")
public class VoiceProviderGuard {

    public VoiceProviderGuard(CareVoiceVoiceProperties properties) {
        String provider = properties.getProvider() == null ? "" : properties.getProvider().trim();
        if (provider.isEmpty()
                || "groq".equalsIgnoreCase(provider)
                || "gemini".equalsIgnoreCase(provider)) {
            return;
        }
        throw new IllegalStateException("CAREVOICE_VOICE_PROVIDER must be groq or gemini.");
    }
}
