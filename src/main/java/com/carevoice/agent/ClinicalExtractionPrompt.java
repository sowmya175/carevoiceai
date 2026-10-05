package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class ClinicalExtractionPrompt {
    private final String systemInstructions;

    public ClinicalExtractionPrompt() {
        this.systemInstructions = readSystemInstructions();
    }

    public String systemInstructions() {
        return systemInstructions;
    }

    public String userMessage(String patientMessage, MonitoringSessionContext context) {
        StringBuilder message = new StringBuilder();
        if (context != null && context.previouslyRequestedField() != null) {
            message.append("Current requested field:\n")
                    .append(context.previouslyRequestedField().name())
                    .append("\n");
            message.append("The requested field is authoritative for directed short answers, even if the previous question wording differs. ")
                    .append("Map only clear answers to that field; acknowledgments such as okay and uncertain answers remain null.\n");
            if (context.previouslyRequestedField() == MonitoringField.PAIN_SCORE) {
                message.append("The patient is answering PAIN_SCORE. ")
                        .append("If the answer contains a rating from 0 through 10, set painScore to that rating. ")
                        .append("Ignore a repeated scale such as \"0 to 10\" or \"1 to 10\". ")
                        .append("Do not treat \"fine\" as five.\n");
            }
            message.append("\n");
        }
        if (context != null && hasText(context.previouslyAskedQuestion())) {
            message.append("Previous question:\n")
                    .append(context.previouslyAskedQuestion().trim())
                    .append("\n\n");
        }
        message.append("Already collected:\n")
                .append(collectedSummary(context))
                .append("\n\nPatient message:\n")
                .append(patientMessage == null ? "" : patientMessage.trim());
        return message.toString();
    }

    private String collectedSummary(MonitoringSessionContext context) {
        if (context == null || context.collectedFacts() == null) {
            return "none";
        }
        CollectedFacts facts = context.collectedFacts();
        StringBuilder summary = new StringBuilder();
        appendFact(summary, "painScore", facts.painScore());
        appendFact(summary, "dizziness", facts.dizziness());
        appendFact(summary, "dizzinessOnset", facts.dizzinessOnset());
        appendFact(summary, "lossOfConsciousness", facts.lossOfConsciousness());
        appendFact(summary, "medicationTaken", facts.medicationTaken());
        appendFact(summary, "appetite", facts.appetite());
        appendFact(summary, "sleepQuality", facts.sleepQuality());
        appendFact(summary, "shortnessOfBreath", facts.shortnessOfBreath());
        appendFact(summary, "temperature", facts.temperature());
        if (summary.isEmpty()) {
            return "none";
        }
        return summary.toString().stripTrailing();
    }

    private void appendFact(StringBuilder summary, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (!summary.isEmpty()) {
            summary.append('\n');
        }
        summary.append(name).append('=').append(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String readSystemInstructions() {
        ClassPathResource resource = new ClassPathResource("prompts/clinical-extraction-system.txt");
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Clinical extraction instructions could not be loaded.", ex);
        }
    }
}
