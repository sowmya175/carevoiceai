package com.carevoice.config;

import com.carevoice.agent.ClinicalExtractionPrompt;
import com.carevoice.agent.ClinicalExtractionService;
import com.carevoice.agent.ClinicalExtractionValidator;
import com.carevoice.agent.FallbackClinicalExtractionService;
import com.carevoice.agent.GeminiClinicalExtractionService;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalExtractionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    GeminiConfiguration.class,
                    RuleBasedClinicalExtractionService.class,
                    GeminiClinicalExtractionService.class,
                    FallbackClinicalExtractionService.class,
                    ClinicalExtractionPrompt.class,
                    ClinicalExtractionValidator.class);

    @Test
    void disabledAiSelectsRuleBasedExtraction() {
        runner.withPropertyValues("carevoice.ai.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ClinicalExtractionService.class);
                    assertThat(context.getBean(ClinicalExtractionService.class))
                            .isInstanceOf(RuleBasedClinicalExtractionService.class);
                    assertThat(context).doesNotHaveBean(Client.class);
                    assertThat(context).doesNotHaveBean(GeminiClinicalExtractionService.class);
                });
    }

    @Test
    void enabledAiWithoutApiKeyFailsConfiguration() {
        runner.withPropertyValues("carevoice.ai.enabled=true", "GOOGLE_API_KEY=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("CAREVOICE_AI_ENABLED is true but GOOGLE_API_KEY is not set.");
                });
    }

    @Test
    void enabledAiSelectsGeminiBackedFallback() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=true",
                        "carevoice.ai.model=gemini-3.8-flash",
                        "GOOGLE_API_KEY=unit-test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ClinicalExtractionService.class))
                            .isInstanceOf(FallbackClinicalExtractionService.class);
                    assertThat(context).hasSingleBean(Client.class);
                    assertThat(context.getBean(CareVoiceAiProperties.class).getModel()).isEqualTo("gemini-3.8-flash");
                });
    }
}
