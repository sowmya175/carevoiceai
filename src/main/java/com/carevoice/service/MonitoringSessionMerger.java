package com.carevoice.service;

import com.carevoice.domain.MonitoringSession;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MonitoringSessionMerger {

    public void merge(MonitoringSession session, ExtractedClinicalFacts facts) {
        if (facts.painScore() != null) {
            session.setPainScore(facts.painScore());
        }
        if (facts.dizziness() != null) {
            session.setDizziness(facts.dizziness());
        }
        if (hasText(facts.dizzinessOnset())) {
            session.setDizzinessOnset(facts.dizzinessOnset().trim());
        }
        if (facts.lossOfConsciousness() != null) {
            session.setLossOfConsciousness(facts.lossOfConsciousness());
        }
        if (facts.medicationTaken() != null) {
            session.setMedicationTaken(facts.medicationTaken());
        }
        if (hasText(facts.appetite())) {
            session.setAppetite(facts.appetite().trim());
            session.setAppetiteReduced(reducedAppetite(facts.appetite()));
        }
        if (hasText(facts.sleepQuality())) {
            session.setSleepQuality(facts.sleepQuality().trim());
        }
        if (facts.shortnessOfBreath() != null) {
            session.setShortnessOfBreath(facts.shortnessOfBreath());
        }
        if (facts.temperature() != null) {
            session.setTemperature(facts.temperature());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Boolean reducedAppetite(String appetite) {
        return switch (appetite.trim().toLowerCase(Locale.ROOT)) {
            case "reduced", "poor" -> true;
            case "good", "normal" -> false;
            default -> null;
        };
    }
}
