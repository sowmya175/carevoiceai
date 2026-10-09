package com.carevoice.agent;
import com.carevoice.integration.gemini.ClinicalExtractionPrompt;
import com.carevoice.integration.gemini.ClinicalExtractionSchema;
import com.carevoice.service.ClinicalExtractionValidator;
import com.carevoice.service.CollectedFacts;
import com.carevoice.service.ExtractedClinicalFacts;
import com.carevoice.integration.gemini.GeminiClinicalExtractionService;
import com.carevoice.service.MonitoringSessionContext;
import com.carevoice.domain.Patient;

import com.carevoice.config.CareVoiceAiProperties;
import com.google.genai.Client;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClinicalExtractionModelOutputTest {
    private final ClinicalExtractionValidator validator = new ClinicalExtractionValidator();
    private final ClinicalExtractionPrompt prompt = new ClinicalExtractionPrompt();
    private final GeminiClinicalExtractionService gemini = new GeminiClinicalExtractionService(
            mock(Client.class),
            new CareVoiceAiProperties(),
            prompt,
            validator);

    @Test
    void mapsStructuredPayloadIntoExtractedFacts() {
        ExtractedClinicalFacts facts = gemini.parseStructuredJson("""
                {
                  "painScore": 6,
                  "dizziness": false,
                  "lossOfConsciousness": false,
                  "appetite": " normal ",
                  "sleepQuality": "good",
                  "temperature": 100.2
                }
                """);

        assertThat(facts.painScore()).isEqualTo(6);
        assertThat(facts.dizziness()).isFalse();
        assertThat(facts.lossOfConsciousness()).isFalse();
        assertThat(facts.medicationTaken()).isNull();
        assertThat(facts.appetite()).isEqualTo("normal");
        assertThat(facts.sleepQuality()).isEqualTo("good");
        assertThat(facts.shortnessOfBreath()).isNull();
        assertThat(facts.temperature()).isEqualTo(100.2);
        assertThat(facts.dizzinessOnset()).isNull();
    }

    @Test
    void rejectsPainScoresOutsideZeroToTen() {
        assertThat(gemini.parseStructuredJson("{\"painScore\":14}").painScore()).isNull();
    }

    @Test
    void rejectsUnsupportedNormalizedStrings() {
        ExtractedClinicalFacts facts = gemini.parseStructuredJson("""
                {"appetite":"excellent","sleepQuality":"restless"}
                """);

        assertThat(facts.appetite()).isNull();
        assertThat(facts.sleepQuality()).isNull();
    }

    @Test
    void blankStringsBecomeNull() {
        ExtractedClinicalFacts facts = gemini.parseStructuredJson("""
                {"dizzinessOnset":"   ","appetite":"","sleepQuality":" "}
                """);

        assertThat(facts.dizzinessOnset()).isNull();
        assertThat(facts.appetite()).isNull();
        assertThat(facts.sleepQuality()).isNull();
    }

    @Test
    void structuredOutputSchemaRequiresNullableMonitoringFields() {
        Schema schema = ClinicalExtractionSchema.responseSchema();

        assertThat(schema.type().orElseThrow().knownEnum()).isEqualTo(Type.Known.OBJECT);
        assertThat(schema.required().orElseThrow()).containsExactly(
                "painScore",
                "dizziness",
                "dizzinessOnset",
                "lossOfConsciousness",
                "medicationTaken",
                "appetite",
                "sleepQuality",
                "shortnessOfBreath",
                "temperature");
        assertThat(schema.properties().orElseThrow().values())
                .allSatisfy(property -> assertThat(property.nullable()).contains(true));
    }

    @Test
    void promptCarriesOnlyTheContextNeededToInterpretTheMessage() {
        MonitoringSessionContext context = new MonitoringSessionContext(
                42L,
                99L,
                new CollectedFacts(6, true, "this morning", null, null, null, null, null, null),
                com.carevoice.domain.SessionStatus.IN_PROGRESS,
                com.carevoice.domain.RiskLevel.YELLOW,
                "Did you faint or lose consciousness?",
                com.carevoice.domain.MonitoringField.LOSS_OF_CONSCIOUSNESS,
                2
        );

        String userMessage = prompt.userMessage("No.", context);

        assertThat(prompt.systemInstructions()).contains("Do not diagnose.");
        assertThat(prompt.systemInstructions()).contains("Null means unknown or not mentioned.");
        assertThat(userMessage).contains("LOSS_OF_CONSCIOUSNESS");
        assertThat(userMessage).contains("Did you faint or lose consciousness?");
        assertThat(userMessage).contains("painScore=6");
        assertThat(userMessage).contains("dizziness=true");
        assertThat(userMessage).contains("dizzinessOnset=this morning");
        assertThat(userMessage).contains("Patient message:\nNo.");

        String painMessage = prompt.userMessage(
                "0 to 10 the pain was at 5.",
                new MonitoringSessionContext(
                        null,
                        null,
                        CollectedFacts.unknown(),
                        com.carevoice.domain.SessionStatus.IN_PROGRESS,
                        com.carevoice.domain.RiskLevel.GREEN,
                        "On a scale from 0 to 10, how would you rate your pain today?",
                        com.carevoice.domain.MonitoringField.PAIN_SCORE,
                        1));
        assertThat(painMessage).contains("Current requested field:\nPAIN_SCORE");
        assertThat(painMessage).contains("The patient is answering PAIN_SCORE.");
        assertThat(prompt.systemInstructions()).contains("When the current requested field is PAIN_SCORE");
        assertThat(userMessage).doesNotContain("YELLOW");
        assertThat(userMessage).doesNotContain("IN_PROGRESS");
        assertThat(userMessage).doesNotContain("42");
        assertThat(userMessage).doesNotContain("99");
    }
}
