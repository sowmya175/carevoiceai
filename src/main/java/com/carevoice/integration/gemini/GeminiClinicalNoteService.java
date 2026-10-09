package com.carevoice.integration.gemini;
import com.carevoice.exception.ClinicalNoteGenerationException;
import com.carevoice.service.ClinicalNoteService;
import com.carevoice.service.ExtractedFactsJson;

import com.carevoice.service.ExtractedClinicalFacts;
import com.carevoice.config.CareVoiceAiProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "carevoice.ai", name = "enabled", havingValue = "true")
public class GeminiClinicalNoteService implements ClinicalNoteService {
    private final Client client;
    private final CareVoiceAiProperties properties;
    private final String systemInstructions;

    public GeminiClinicalNoteService(Client client, CareVoiceAiProperties properties) {
        this.client = client;
        this.properties = properties;
        this.systemInstructions = readPrompt();
    }

    @Override
    public String write(String question, String patientResponse, ExtractedClinicalFacts facts) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemInstructions)))
                .build();
        try {
            GenerateContentResponse response = client.models.generateContent(
                    properties.getModel(),
                    userMessage(question, patientResponse, facts),
                    config);
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new ClinicalNoteGenerationException();
            }
            return text.trim();
        } catch (ApiException | GenAiIOException | IllegalArgumentException ex) {
            throw new ClinicalNoteGenerationException();
        }
    }

    @Override
    public String provider() {
        return "gemini";
    }

    public String model() {
        return properties.getModel();
    }

    private static String userMessage(String question, String patientResponse, ExtractedClinicalFacts facts) {
        return "Question:\n"
                + (question == null ? "" : question.trim())
                + "\n\nPatient response:\n"
                + (patientResponse == null ? "" : patientResponse.trim())
                + "\n\nStructured facts:\n"
                + ExtractedFactsJson.write(facts);
    }

    private static String readPrompt() {
        try (InputStream input = new ClassPathResource("prompts/clinical-note-system.txt").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Clinical note prompt could not be loaded");
        }
    }
}
