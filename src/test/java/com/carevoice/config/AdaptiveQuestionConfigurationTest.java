package com.carevoice.config;

import com.carevoice.wording.AdaptiveQuestionWordingService;
import com.carevoice.wording.DeterministicQuestionWordingService;
import com.carevoice.wording.GeminiQuestionWordingService;
import com.carevoice.wording.QuestionWordingService;
import com.carevoice.wording.QuestionWordingValidator;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveQuestionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    GeminiConfiguration.class,
                    AdaptiveQuestionWordingService.class,
                    DeterministicQuestionWordingService.class,
                    GeminiQuestionWordingService.class,
                    QuestionWordingValidator.class);

    @Test
    void adaptiveQuestionsStayOffUnlessBothFlagsAreTrue() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=true",
                        "carevoice.ai.adaptive-questions-enabled=false",
                        "GOOGLE_API_KEY=unit-test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GeminiQuestionWordingService.class);
                    assertThat(context.getBean(QuestionWordingService.class))
                            .isInstanceOf(AdaptiveQuestionWordingService.class);
                    assertThat(context.getBean(CareVoiceAiProperties.class).isAdaptiveQuestionsEnabled()).isFalse();
                    assertThat(context.getBean(CareVoiceAiProperties.class).getTimeoutMillis()).isEqualTo(12_000);
                });
    }

    @Test
    void aiDisabledDoesNotRequireAKeyForAdaptiveQuestions() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=false",
                        "carevoice.ai.adaptive-questions-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(Client.class);
                    assertThat(context).doesNotHaveBean(GeminiQuestionWordingService.class);
                });
    }

    @Test
    void bothFlagsCreateTheGeminiWordingService() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=true",
                        "carevoice.ai.adaptive-questions-enabled=true",
                        "carevoice.ai.model=gemini-3.8-flash",
                        "GOOGLE_API_KEY=unit-test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GeminiQuestionWordingService.class);
                    assertThat(context.getBean(CareVoiceAiProperties.class).isAdaptiveQuestionsEnabled()).isTrue();
                    assertThat(context.getBean(CareVoiceAiProperties.class).getModel()).isEqualTo("gemini-3.8-flash");
                });
    }
}
