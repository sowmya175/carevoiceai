package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.google.genai.errors.GenAiIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClinicalExtractionRoutingTest {
    private final ClinicalExtractionRoutingPolicy policy = new ClinicalExtractionRoutingPolicy();
    private final RuleBasedClinicalExtractionService rules = new RuleBasedClinicalExtractionService();

    @ParameterizedTest
    @CsvSource({
            "MEDICATION_TAKEN, 'Yes, I took it.', true",
            "LOSS_OF_CONSCIOUSNESS, 'No.', false"
    })
    void directedBooleanAnswersDoNotCallGemini(MonitoringField field, String message, boolean expected) {
        GeminiClinicalExtractionService gemini = geminiThatMustNotBeCalled();

        ExtractedClinicalFacts facts = service(gemini).extract(message, asked(field));

        if (field == MonitoringField.MEDICATION_TAKEN) {
            assertThat(facts.medicationTaken()).isEqualTo(expected);
        } else {
            assertThat(facts.lossOfConsciousness()).isEqualTo(expected);
        }
        verify(gemini, never()).extract(any(), any());
    }

    @Test
    void directedPainDoesNotCallGemini() {
        GeminiClinicalExtractionService gemini = geminiThatMustNotBeCalled();

        ExtractedClinicalFacts facts = service(gemini).extract("Five.", asked(MonitoringField.PAIN_SCORE));

        assertThat(facts.painScore()).isEqualTo(5);
        verify(gemini, never()).extract(any(), any());
    }

    @Test
    void directedSleepDoesNotCallGemini() {
        GeminiClinicalExtractionService gemini = geminiThatMustNotBeCalled();

        ExtractedClinicalFacts facts = service(gemini).extract(
                "I kept waking up all night.", asked(MonitoringField.SLEEP_QUALITY));

        assertThat(facts.sleepQuality()).isEqualTo("poor");
        verify(gemini, never()).extract(any(), any());
    }

    @Test
    void directedAppetiteDoesNotCallGemini() {
        GeminiClinicalExtractionService gemini = geminiThatMustNotBeCalled();

        ExtractedClinicalFacts facts = service(gemini).extract(
                "I haven't felt hungry.", asked(MonitoringField.APPETITE));

        assertThat(facts.appetite()).isEqualTo("reduced");
        verify(gemini, never()).extract(any(), any());
    }

    @Test
    void resolvedMedicationKeepsAnExplicitSafetyFact() {
        GeminiClinicalExtractionService gemini = geminiThatMustNotBeCalled();

        ExtractedClinicalFacts facts = service(gemini).extract(
                "Yes, I took it and I have shortness of breath.",
                asked(MonitoringField.MEDICATION_TAKEN));

        assertThat(facts.medicationTaken()).isTrue();
        assertThat(facts.shortnessOfBreath()).isTrue();
        verify(gemini, never()).extract(any(), any());
    }

    @Test
    void unresolvedDirectedAnswerCallsGeminiOnce() {
        GeminiClinicalExtractionService gemini = mock(GeminiClinicalExtractionService.class);
        ExtractedClinicalFacts geminiFacts = new ExtractedClinicalFacts(
                null, null, null, null, true, null, null, null, null);
        when(gemini.extract(eq("Not sure."), any())).thenReturn(geminiFacts);

        ExtractedClinicalFacts facts = service(gemini).extract("Not sure.", asked(MonitoringField.MEDICATION_TAKEN));

        assertThat(facts).isSameAs(geminiFacts);
        verify(gemini, times(1)).extract(eq("Not sure."), any());
    }

    @Test
    void openEndedResponseMayCallGeminiAfterRules() {
        GeminiClinicalExtractionService gemini = mock(GeminiClinicalExtractionService.class);
        String message = "I have been feeling strange since yesterday. I get lightheaded when I stand, "
                + "my breathing feels different, and I barely slept.";
        ExtractedClinicalFacts geminiFacts = new ExtractedClinicalFacts(
                null, true, null, null, null, null, "poor", null, null);
        when(gemini.extract(eq(message), any())).thenReturn(geminiFacts);

        ExtractedClinicalFacts facts = service(gemini).extract(message, MonitoringSessionContext.empty());

        assertThat(facts).isSameAs(geminiFacts);
        assertThat(rules.extract(message, MonitoringSessionContext.empty()).dizziness()).isTrue();
        verify(gemini, times(1)).extract(eq(message), any());
    }

    @Test
    void geminiFailureKeepsTheDeterministicFacts() {
        GeminiClinicalExtractionService gemini = mock(GeminiClinicalExtractionService.class);
        when(gemini.extract(eq("My pain is 6."), any())).thenThrow(new GenAiIOException("provider unavailable"));

        ExtractedClinicalFacts facts = service(gemini).extract("My pain is 6.", MonitoringSessionContext.empty());

        assertThat(facts.painScore()).isEqualTo(6);
        verify(gemini, times(1)).extract(eq("My pain is 6."), any());
    }

    @Test
    void explicitFalseResolvesADirectedField() {
        ExtractedClinicalFacts denied = new ExtractedClinicalFacts(
                null, null, null, false, null, null, null, null, null);
        assertThat(policy.geminiRequired(denied, asked(MonitoringField.LOSS_OF_CONSCIOUSNESS))).isFalse();
        assertThat(policy.geminiRequired(ExtractedClinicalFacts.none(), asked(MonitoringField.LOSS_OF_CONSCIOUSNESS)))
                .isTrue();
        assertThat(policy.geminiRequired(denied, MonitoringSessionContext.empty())).isTrue();
    }

    private FallbackClinicalExtractionService service(GeminiClinicalExtractionService gemini) {
        return new FallbackClinicalExtractionService(gemini, rules, policy);
    }

    private static GeminiClinicalExtractionService geminiThatMustNotBeCalled() {
        GeminiClinicalExtractionService gemini = mock(GeminiClinicalExtractionService.class);
        when(gemini.extract(any(), any())).thenThrow(new GenAiIOException("provider unavailable"));
        return gemini;
    }

    private static MonitoringSessionContext asked(MonitoringField field) {
        return new MonitoringSessionContext(
                1L,
                9L,
                CollectedFacts.unknown(),
                SessionStatus.IN_PROGRESS,
                RiskLevel.GREEN,
                "Current question",
                field,
                1);
    }
}
