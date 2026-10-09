package com.carevoice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlanModelStartupTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PlanModelConfiguration.class);

    @Test
    void disabledPlanAiDoesNotRequireATunedModel() {
        runner.withPropertyValues("carevoice.plan-ai.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void vertexTunedWithoutAModelFailsStartup() {
        runner.withPropertyValues(
                        "carevoice.plan-ai.enabled=true",
                        "carevoice.plan-ai.provider=vertex-tuned",
                        "carevoice.plan-ai.base-model=gemini-3.5-flash",
                        "carevoice.plan-ai.tuned-model=",
                        "carevoice.plan-ai.project-id=demo",
                        "carevoice.plan-ai.location=us-central1")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("CAREVOICE_PLAN_TUNED_MODEL"));
    }

    @Test
    void extractionModelIsNotAcceptedAsThePlanBase() {
        runner.withPropertyValues(
                        "carevoice.plan-ai.enabled=true",
                        "carevoice.plan-ai.provider=gemini-base",
                        "carevoice.plan-ai.base-model=gemini-3.8-flash",
                        "GOOGLE_API_KEY=unit-test-key")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("gemini-3.8-flash"));
    }
}
