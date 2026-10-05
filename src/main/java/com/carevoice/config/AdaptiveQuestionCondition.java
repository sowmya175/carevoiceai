package com.carevoice.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AdaptiveQuestionCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return enabled(environment.getProperty("carevoice.ai.enabled"))
                && enabled(environment.getProperty("carevoice.ai.adaptive-questions-enabled"));
    }

    private static boolean enabled(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
