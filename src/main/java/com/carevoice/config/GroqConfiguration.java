package com.carevoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class GroqConfiguration {
    public static final String MISSING_API_KEY =
            "CAREVOICE_VOICE_ENABLED is true and provider is groq, but GROQ_API_KEY is not set.";

    @Bean
    @Conditional(GroqVoiceCondition.class)
    public RestClient groqRestClient(CareVoiceVoiceProperties properties) {
        String apiKey = properties.getGroq().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(MISSING_API_KEY);
        }
        String baseUrl = properties.getGroq().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("GROQ_BASE_URL is not set.");
        }
        return restClientBuilder(baseUrl, apiKey.trim()).build();
    }

    public static RestClient.Builder restClientBuilder(String baseUrl, String apiKey) {
        JsonMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return RestClient.builder()
                .baseUrl(stripTrailingSlash(baseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .configureMessageConverters(converters -> converters.configureMessageConvertersList(list -> {
                    list.clear();
                    list.add(new FormHttpMessageConverter());
                    list.add(new JacksonJsonHttpMessageConverter(mapper));
                }));
    }

    private static String stripTrailingSlash(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
