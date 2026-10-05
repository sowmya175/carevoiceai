package com.carevoice.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GroqVoiceCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return groqVoice(context.getEnvironment());
    }

    static boolean groqVoice(Environment environment) {
        if (!"true".equalsIgnoreCase(environment.getProperty("carevoice.voice.enabled"))) {
            return false;
        }
        String provider = environment.getProperty("carevoice.voice.provider");
        if (provider == null || provider.isBlank()) {
            return true;
        }
        return "groq".equalsIgnoreCase(provider.trim());
    }
}
