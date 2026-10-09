package com.carevoice.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties({CareVoiceAiProperties.class, CareVoiceVoiceProperties.class})
public class GeminiConfiguration {

    @Bean(destroyMethod = "close")
    @Primary
    @Conditional(GeminiClientCondition.class)
    public Client geminiClient(Environment environment, CareVoiceAiProperties properties) {
        String apiKey = environment.getProperty("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(missingKeyMessage(environment));
        }
        int timeoutMillis = properties.getTimeoutMillis() > 0 ? properties.getTimeoutMillis() : 12_000;
        return Client.builder()
                .apiKey(apiKey.trim())
                .httpOptions(HttpOptions.builder()
                        .timeout(timeoutMillis)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }

    private String missingKeyMessage(Environment environment) {
        boolean aiEnabled = Boolean.parseBoolean(environment.getProperty("carevoice.ai.enabled"));
        boolean geminiVoice = GeminiVoiceCondition.geminiVoice(environment);
        if (aiEnabled && geminiVoice) {
            return "CAREVOICE_AI_ENABLED and CAREVOICE_VOICE_ENABLED are true but GOOGLE_API_KEY is not set.";
        }
        if (geminiVoice) {
            return "CAREVOICE_VOICE_ENABLED is true but GOOGLE_API_KEY is not set.";
        }
        return "CAREVOICE_AI_ENABLED is true but GOOGLE_API_KEY is not set.";
    }
}
