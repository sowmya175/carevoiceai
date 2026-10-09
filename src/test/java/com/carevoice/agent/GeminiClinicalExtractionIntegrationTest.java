package com.carevoice.agent;
import com.carevoice.integration.gemini.ClinicalExtractionPrompt;
import com.carevoice.service.ClinicalExtractionValidator;
import com.carevoice.service.ExtractedClinicalFacts;
import com.carevoice.integration.gemini.GeminiClinicalExtractionService;
import com.carevoice.service.MonitoringSessionContext;

import com.carevoice.config.CareVoiceAiProperties;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CAREVOICE_RUN_GEMINI_INTEGRATION_TESTS", matches = "true")
class GeminiClinicalExtractionIntegrationTest {

    @Test
    void liveModelExtractsLightheadednessAndPainWithoutInventingOtherFacts() {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        assertThat(apiKey).isNotBlank();

        CareVoiceAiProperties properties = new CareVoiceAiProperties();
        properties.setEnabled(true);
        properties.setModel(System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.8-flash"));

        try (Client client = Client.builder().apiKey(apiKey).build()) {
            GeminiClinicalExtractionService extractor = new GeminiClinicalExtractionService(
                    client,
                    properties,
                    new ClinicalExtractionPrompt(),
                    new ClinicalExtractionValidator());

            ExtractedClinicalFacts facts = extractor.extract(
                    "I've been feeling lightheaded since breakfast and my pain is around three.",
                    MonitoringSessionContext.empty());

            assertThat(facts.painScore()).isEqualTo(3);
            assertThat(facts.dizziness()).isTrue();
            assertThat(facts.dizzinessOnset()).containsIgnoringCase("breakfast");
            assertThat(facts.lossOfConsciousness()).isNull();
            assertThat(facts.medicationTaken()).isNull();
            assertThat(facts.shortnessOfBreath()).isNull();
        }
    }
}
