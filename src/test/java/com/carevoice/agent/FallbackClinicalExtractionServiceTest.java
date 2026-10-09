package com.carevoice.agent;

import com.google.genai.errors.GenAiIOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackClinicalExtractionServiceTest {

    @Test
    void providerFailureUsesRuleBasedExtraction() {
        GeminiClinicalExtractionService gemini = mock(GeminiClinicalExtractionService.class);
        RuleBasedClinicalExtractionService rules = new RuleBasedClinicalExtractionService();
        when(gemini.extract(eq("My pain is 6."), any())).thenThrow(new GenAiIOException("provider unavailable"));

        FallbackClinicalExtractionService fallback = new FallbackClinicalExtractionService(
                gemini, rules, new ClinicalExtractionRoutingPolicy());

        ExtractedClinicalFacts facts = fallback.extract("My pain is 6.", MonitoringSessionContext.empty());

        assertThat(facts.painScore()).isEqualTo(6);
        verify(gemini).extract(eq("My pain is 6."), any());
    }
}
