package com.carevoice.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GeminiVoiceCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return geminiVoice(context.getEnvironment());
    }

    static boolean geminiVoice(Environment environment) {
        return isEnabled(environment.getProperty("carevoice.voice.enabled"))
                && "gemini".equalsIgnoreCase(provider(environment));
    }

    private static boolean isEnabled(String value) {
        return "true".equalsIgnoreCase(value);
    }

    private static String provider(Environment environment) {
        String provider = environment.getProperty("carevoice.voice.provider");
        return provider == null ? "" : provider.trim();
    }
}
