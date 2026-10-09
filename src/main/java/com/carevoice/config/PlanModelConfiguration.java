package com.carevoice.config;

import com.carevoice.proposal.GeminiPlanGenerationModel;
import com.carevoice.proposal.VertexTunedPlanGenerationModel;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(CareVoicePlanAiProperties.class)
public class PlanModelConfiguration {
    @Bean
    PlanModelStartup planModelStartup(CareVoicePlanAiProperties properties, Environment environment) {
        return new PlanModelStartup(properties, environment);
    }

    @Bean(destroyMethod = "close")
    @Conditional(PlanGeminiModelCondition.class)
    Client planGeminiClient(Environment environment, CareVoicePlanAiProperties properties) {
        String apiKey = environment.getProperty("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY is required when the plan provider is gemini-base or fallback to the base plan model is enabled.");
        }
        return client(properties).apiKey(apiKey.trim()).build();
    }

    @Bean(destroyMethod = "close")
    @Conditional(PlanVertexModelCondition.class)
    Client planVertexClient(CareVoicePlanAiProperties properties) {
        return client(properties)
                .vertexAI(true)
                .project(properties.getProjectId().trim())
                .location(properties.getLocation().trim())
                .build();
    }

    @Bean
    @Conditional(PlanGeminiModelCondition.class)
    GeminiPlanGenerationModel geminiPlanGenerationModel(
            @Qualifier("planGeminiClient") Client planGeminiClient, CareVoicePlanAiProperties properties) {
        return new GeminiPlanGenerationModel(planGeminiClient, properties);
    }

    @Bean
    @Conditional(PlanVertexModelCondition.class)
    VertexTunedPlanGenerationModel vertexTunedPlanGenerationModel(
            @Qualifier("planVertexClient") Client planVertexClient, CareVoicePlanAiProperties properties) {
        return new VertexTunedPlanGenerationModel(planVertexClient, properties);
    }

    private static Client.Builder client(CareVoicePlanAiProperties properties) {
        int timeout = properties.getTimeoutMillis() > 0 ? properties.getTimeoutMillis() : 20_000;
        return Client.builder().httpOptions(HttpOptions.builder()
                .timeout(timeout)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build());
    }

    static final class PlanModelStartup {
        private PlanModelStartup(CareVoicePlanAiProperties properties, Environment environment) {
            properties.requireCoherent(environment);
        }
    }
}
