package com.carevoice.integration.gemini;
import com.carevoice.service.QuestionContext;
import com.carevoice.exception.QuestionWordingException;
import com.carevoice.service.QuestionWordingService;

import com.carevoice.service.CollectedFacts;
import com.carevoice.service.PlannedQuestion;
import com.carevoice.config.AdaptiveQuestionCondition;
import com.carevoice.config.CareVoiceAiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@Conditional(AdaptiveQuestionCondition.class)
public class GeminiQuestionWordingService implements QuestionWordingService {
    private static final Logger log = LoggerFactory.getLogger(GeminiQuestionWordingService.class);

    private final Client client;
    private final CareVoiceAiProperties properties;
    private final String systemInstructions;
    private final ObjectMapper objectMapper;

    public GeminiQuestionWordingService(Client client, CareVoiceAiProperties properties) {
        this.client = client;
        this.properties = properties;
        this.systemInstructions = readPrompt();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String generateQuestion(PlannedQuestion plannedQuestion, QuestionContext context) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemInstructions)))
                .responseMimeType("application/json")
                .responseSchema(QuestionWordingSchema.responseSchema())
                .build();
        try {
            GenerateContentResponse response = client.models.generateContent(
                    properties.getModel(),
                    userMessage(plannedQuestion, context),
                    config);
            String question = parse(responseText(response));
            log.info("Gemini question wording completed field={}", plannedQuestion.field());
            return question;
        } catch (ApiException | GenAiIOException | IllegalArgumentException | QuestionWordingException ex) {
            throw new QuestionWordingException();
        }
    }

    public String parse(String json) {
        if (json == null || json.isBlank()) {
            throw new QuestionWordingException();
        }
        try {
            AdaptiveQuestionPayload payload = objectMapper.readValue(json, AdaptiveQuestionPayload.class);
            if (payload == null || payload.question() == null) {
                throw new QuestionWordingException();
            }
            return payload.question();
        } catch (JsonProcessingException ex) {
            throw new QuestionWordingException();
        }
    }

    public static String userMessage(PlannedQuestion plannedQuestion, QuestionContext context) {
        StringBuilder message = new StringBuilder();
        message.append("Required field:\n")
                .append(plannedQuestion.field().name())
                .append("\n\nFallback question:\n")
                .append(plannedQuestion.question() == null ? "" : plannedQuestion.question().trim())
                .append("\n\nPrevious question:\n")
                .append(text(context == null ? null : context.previousQuestion()))
                .append("\n\nLatest patient response:\n")
                .append(text(context == null ? null : context.latestPatientResponse()))
                .append("\n\nKnown facts:\n")
                .append(knownFacts(context == null ? null : context.knownFacts()));
        return message.toString();
    }

    private static String knownFacts(CollectedFacts facts) {
        if (facts == null) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        append(summary, "painScore", facts.painScore());
        append(summary, "dizziness", facts.dizziness());
        append(summary, "dizzinessOnset", facts.dizzinessOnset());
        append(summary, "lossOfConsciousness", facts.lossOfConsciousness());
        append(summary, "medicationTaken", facts.medicationTaken());
        append(summary, "appetite", facts.appetite());
        append(summary, "sleepQuality", facts.sleepQuality());
        append(summary, "shortnessOfBreath", facts.shortnessOfBreath());
        append(summary, "temperature", facts.temperature());
        if (summary.isEmpty()) {
            return "none";
        }
        return summary.toString().stripTrailing();
    }

    private static void append(StringBuilder summary, String name, Object value) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return;
        }
        if (!summary.isEmpty()) {
            summary.append('\n');
        }
        summary.append(name).append('=').append(value);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }

    private String responseText(GenerateContentResponse response) {
        try {
            return response.text();
        } catch (IllegalArgumentException ex) {
            throw new QuestionWordingException();
        }
    }

    private static String readPrompt() {
        try (InputStream input = new ClassPathResource("prompts/question-wording-system.txt").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Question wording prompt could not be loaded");
        }
    }
}
