package com.carevoice.integration.gemini;
import com.carevoice.service.ClinicalExtractionService;
import com.carevoice.service.ClinicalExtractionValidator;
import com.carevoice.service.ExtractedClinicalFacts;
import com.carevoice.exception.GeminiExtractionException;
import com.carevoice.service.MonitoringSessionContext;

import com.carevoice.config.CareVoiceAiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "carevoice.ai", name = "enabled", havingValue = "true")
public class GeminiClinicalExtractionService implements ClinicalExtractionService {
    private static final Logger log = LoggerFactory.getLogger(GeminiClinicalExtractionService.class);

    private final Client client;
    private final CareVoiceAiProperties properties;
    private final ClinicalExtractionPrompt prompt;
    private final ClinicalExtractionValidator validator;
    private final ObjectMapper objectMapper;

    public GeminiClinicalExtractionService(
            Client client,
            CareVoiceAiProperties properties,
            ClinicalExtractionPrompt prompt,
            ClinicalExtractionValidator validator) {
        this.client = client;
        this.properties = properties;
        this.prompt = prompt;
        this.validator = validator;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public ExtractedClinicalFacts extract(String message, MonitoringSessionContext context) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(prompt.systemInstructions())))
                .responseMimeType("application/json")
                .responseSchema(ClinicalExtractionSchema.responseSchema())
                .build();

        GenerateContentResponse response = client.models.generateContent(
                properties.getModel(),
                prompt.userMessage(message, context),
                config);

        ExtractedClinicalFacts facts = validator.validate(parseStructuredJson(responseText(response)), message, context);
        log.info("Gemini clinical extraction completed sessionId={}", sessionId(context));
        return facts;
    }

    public ExtractedClinicalFacts parseStructuredJson(String json) {
        if (json == null || json.isBlank()) {
            throw new GeminiExtractionException("Gemini clinical extraction returned no structured output");
        }
        try {
            ClinicalExtractionPayload payload = objectMapper.readValue(json, ClinicalExtractionPayload.class);
            return validator.validate(payload.toFacts());
        } catch (JsonProcessingException ex) {
            throw new GeminiExtractionException("Gemini clinical extraction returned unreadable structured output");
        }
    }

    private String responseText(GenerateContentResponse response) {
        try {
            return response.text();
        } catch (IllegalArgumentException ex) {
            throw new GeminiExtractionException("Gemini clinical extraction returned no structured output");
        }
    }

    private Long sessionId(MonitoringSessionContext context) {
        return context == null ? null : context.sessionId();
    }
}
