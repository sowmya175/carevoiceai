package com.carevoice.config;

import com.google.genai.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties({CareVoiceAiProperties.class, CareVoiceVoiceProperties.class})
public class GeminiConfiguration {

    @Bean(destroyMethod = "close")
    @Conditional(GeminiClientCondition.class)
    public Client geminiClient(Environment environment) {
        String apiKey = environment.getProperty("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(missingKeyMessage(environment));
        }
        return Client.builder().apiKey(apiKey.trim()).build();
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
