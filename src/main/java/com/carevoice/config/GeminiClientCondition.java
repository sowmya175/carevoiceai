package com.carevoice.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GeminiClientCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return isEnabled(environment.getProperty("carevoice.ai.enabled"))
                || GeminiVoiceCondition.geminiVoice(environment);
    }

    private boolean isEnabled(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
