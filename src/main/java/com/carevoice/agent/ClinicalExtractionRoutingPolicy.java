package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import org.springframework.stereotype.Component;

/**
 * Decides whether a turn still needs Gemini after deterministic extraction.
 * A directed question is resolved only when the requested field itself is present.
 * Other facts on the same result are kept either way; they do not by themselves
 * authorize skipping Gemini, and they are not discarded when Gemini is skipped.
 */
@Component
public class ClinicalExtractionRoutingPolicy {

    public boolean geminiRequired(ExtractedClinicalFacts facts, MonitoringSessionContext context) {
        MonitoringField requested = context == null ? null : context.previouslyRequestedField();
        if (requested == null) {
            return true;
        }
        return !requestedFieldResolved(facts, requested);
    }

    public boolean requestedFieldResolved(ExtractedClinicalFacts facts, MonitoringField field) {
        if (facts == null || field == null) {
            return false;
        }
        return switch (field) {
            case PAIN_SCORE -> facts.painScore() != null;
            case DIZZINESS_ONSET -> text(facts.dizzinessOnset());
            case LOSS_OF_CONSCIOUSNESS -> facts.lossOfConsciousness() != null;
            case MEDICATION_TAKEN -> facts.medicationTaken() != null;
            case APPETITE -> text(facts.appetite());
            case SLEEP_QUALITY -> text(facts.sleepQuality());
            case TEMPERATURE -> facts.temperature() != null;
        };
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
