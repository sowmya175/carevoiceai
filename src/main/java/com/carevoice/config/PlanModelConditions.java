package com.carevoice.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class PlanGeminiModelCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        if (!enabled(environment)) {
            return false;
        }
        String provider = environment.getProperty("carevoice.plan-ai.provider", "");
        boolean fallback = "true".equalsIgnoreCase(environment.getProperty("carevoice.plan-ai.fallback-to-base"));
        return "gemini-base".equalsIgnoreCase(provider) || fallback;
    }

    static boolean enabled(Environment environment) {
        return "true".equalsIgnoreCase(environment.getProperty("carevoice.plan-ai.enabled"));
    }
}

final class PlanVertexModelCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        if (!PlanGeminiModelCondition.enabled(environment)
                || !"vertex-tuned".equalsIgnoreCase(environment.getProperty("carevoice.plan-ai.provider", ""))) {
            return false;
        }
        return present(environment, "carevoice.plan-ai.tuned-model")
                && present(environment, "carevoice.plan-ai.project-id")
                && present(environment, "carevoice.plan-ai.location");
    }

    private static boolean present(Environment environment, String key) {
        String value = environment.getProperty(key);
        return value != null && !value.isBlank();
    }
}
