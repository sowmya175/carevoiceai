package com.carevoice.config;

import com.carevoice.agent.ClinicalExtractionPrompt;
import com.carevoice.agent.ClinicalExtractionService;
import com.carevoice.agent.ClinicalExtractionValidator;
import com.carevoice.agent.FallbackClinicalExtractionService;
import com.carevoice.agent.GeminiClinicalExtractionService;
import com.carevoice.agent.RuleBasedClinicalExtractionService;
import com.carevoice.voice.AudioTranscriptionService;
import com.carevoice.voice.GeminiAudioTranscriptionService;
import com.carevoice.voice.GroqWhisperAudioTranscriptionService;
import com.carevoice.voice.VoiceMonitoringService;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    GeminiConfiguration.class,
                    GroqConfiguration.class,
                    VoiceProviderGuard.class,
                    RuleBasedClinicalExtractionService.class,
                    GeminiClinicalExtractionService.class,
                    FallbackClinicalExtractionService.class,
                    GeminiAudioTranscriptionService.class,
                    GroqWhisperAudioTranscriptionService.class,
                    ClinicalExtractionPrompt.class,
                    ClinicalExtractionValidator.class);

    @Test
    void aiDisabledAndVoiceDisabledDoesNotRequireAnApiKey() {
        runner.withPropertyValues("carevoice.ai.enabled=false", "carevoice.voice.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ClinicalExtractionService.class))
                            .isInstanceOf(RuleBasedClinicalExtractionService.class);
                    assertThat(context).doesNotHaveBean(Client.class);
                    assertThat(context).doesNotHaveBean(RestClient.class);
                    assertThat(context).doesNotHaveBean(GeminiAudioTranscriptionService.class);
                    assertThat(context).doesNotHaveBean(GroqWhisperAudioTranscriptionService.class);
                });
    }

    @Test
    void groqVoiceWithoutApiKeyFailsConfiguration() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=false",
                        "carevoice.voice.enabled=true",
                        "carevoice.voice.provider=groq",
                        "carevoice.voice.groq.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(GroqConfiguration.MISSING_API_KEY);
                });
    }

    @Test
    void groqIsTheDefaultTranscriptionProvider() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=false",
                        "carevoice.voice.enabled=true",
                        "carevoice.voice.groq.model=whisper-large-v3-turbo",
                        "carevoice.voice.groq.base-url=https://api.groq.com/openai/v1",
                        "carevoice.voice.groq.api-key=unit-test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ClinicalExtractionService.class))
                            .isInstanceOf(RuleBasedClinicalExtractionService.class);
                    assertThat(context).doesNotHaveBean(GeminiClinicalExtractionService.class);
                    assertThat(context).doesNotHaveBean(Client.class);
                    assertThat(context).doesNotHaveBean(GeminiAudioTranscriptionService.class);
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(GroqWhisperAudioTranscriptionService.class);
                    assertThat(context.getBean(AudioTranscriptionService.class))
                            .isInstanceOf(GroqWhisperAudioTranscriptionService.class);
                    CareVoiceVoiceProperties voice = context.getBean(CareVoiceVoiceProperties.class);
                    assertThat(voice.getProvider()).isEqualTo("groq");
                    assertThat(voice.getGroq().getModel()).isEqualTo("whisper-large-v3-turbo");
                    assertThat(voice.getGroq().getBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
                    assertThat(voiceServiceDependencies()).noneMatch(type ->
                            type.contains("openai") || type.contains("genai") || type.contains("google")
                                    || type.contains("groq"));
                });
    }

    @Test
    void geminiTranscriptionRemainsAvailableWhenSelected() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=false",
                        "carevoice.voice.enabled=true",
                        "carevoice.voice.provider=gemini",
                        "carevoice.voice.gemini-transcription-model=gemini-3.5-transcribe",
                        "GOOGLE_API_KEY=unit-test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ClinicalExtractionService.class))
                            .isInstanceOf(RuleBasedClinicalExtractionService.class);
                    assertThat(context).doesNotHaveBean(RestClient.class);
                    assertThat(context).doesNotHaveBean(GroqWhisperAudioTranscriptionService.class);
                    assertThat(context).hasSingleBean(Client.class);
                    assertThat(context).hasSingleBean(GeminiAudioTranscriptionService.class);
                    assertThat(context.getBean(CareVoiceVoiceProperties.class).getGeminiTranscriptionModel())
                            .isEqualTo("gemini-3.5-transcribe");
                });
    }

    @Test
    void geminiTranscriptionWithoutGoogleKeyFailsConfiguration() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=false",
                        "carevoice.voice.enabled=true",
                        "carevoice.voice.provider=gemini",
                        "GOOGLE_API_KEY=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("CAREVOICE_VOICE_ENABLED is true but GOOGLE_API_KEY is not set.");
                });
    }

    @Test
    void groqTranscriptionAndGeminiExtractionCanBeEnabledTogether() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=true",
                        "carevoice.voice.enabled=true",
                        "carevoice.voice.provider=groq",
                        "carevoice.ai.model=gemini-3.8-flash",
                        "carevoice.voice.groq.model=whisper-large-v3-turbo",
                        "carevoice.voice.groq.api-key=unit-test-groq-key",
                        "GOOGLE_API_KEY=unit-test-google-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ClinicalExtractionService.class))
                            .isInstanceOf(FallbackClinicalExtractionService.class);
                    assertThat(context).hasSingleBean(Client.class);
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(GroqWhisperAudioTranscriptionService.class);
                    assertThat(context).doesNotHaveBean(GeminiAudioTranscriptionService.class);
                    assertThat(context.getBean(CareVoiceAiProperties.class).getModel()).isEqualTo("gemini-3.8-flash");
                    assertThat(context.getBean(CareVoiceVoiceProperties.class).getGroq().getModel())
                            .isEqualTo("whisper-large-v3-turbo");
                });
    }

    @Test
    void unknownVoiceProviderFailsConfiguration() {
        runner.withPropertyValues(
                        "carevoice.ai.enabled=false",
                        "carevoice.voice.enabled=true",
                        "carevoice.voice.provider=whisper",
                        "carevoice.voice.groq.api-key=unit-test-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("CAREVOICE_VOICE_PROVIDER must be groq or gemini.");
                });
    }

    private static java.util.List<String> voiceServiceDependencies() {
        java.util.List<String> types = new java.util.ArrayList<>();
        for (Field field : VoiceMonitoringService.class.getDeclaredFields()) {
            if (field.getType().getName().startsWith("org.slf4j")) {
                continue;
            }
            types.add(field.getType().getName().toLowerCase(java.util.Locale.ROOT));
        }
        return types;
    }
}
