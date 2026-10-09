package com.carevoice.service;

import com.carevoice.domain.MonitoringField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class ClinicalExtractionValidator {
    private static final Logger log = LoggerFactory.getLogger(ClinicalExtractionValidator.class);
    private static final Set<String> APPETITE = Set.of("good", "normal", "reduced", "poor");
    private static final Set<String> SLEEP = Set.of("good", "normal", "poor");
    private static final double MIN_TEMPERATURE = 30.0;
    private static final double MAX_TEMPERATURE = 115.0;

    /** Context checks apply to provider output as well as the deterministic fallback. */
    public ExtractedClinicalFacts validate(ExtractedClinicalFacts facts, String message, MonitoringSessionContext context) {
        String text = message == null ? "" : message.strip().toLowerCase(Locale.ROOT).replace('’', '\'');
        MonitoringField field = context == null ? null : context.previouslyRequestedField();
        if (text.matches("(?:okay|ok|maybe|not sure|i don't know|i do not know|unsure)[.!?]*")) {
            return ExtractedClinicalFacts.none();
        }
        boolean numericAnswer = text.matches("[+-]?\\d+(?:\\.\\d+)?[.!?]?");
        Double temperature = facts.temperature();
        if (numericAnswer) {
            temperature = field == MonitoringField.TEMPERATURE
                    ? Double.valueOf(text.replaceFirst("[.!?]$", "")) : null;
        }
        return validate(new ExtractedClinicalFacts(
                facts.painScore(), facts.dizziness(), facts.dizzinessOnset(),
                nearSyncope(text) ? null : facts.lossOfConsciousness(), facts.medicationTaken(),
                field == MonitoringField.APPETITE && text.matches("could be better[.!?]*") ? null : facts.appetite(),
                field == MonitoringField.SLEEP_QUALITY && text.matches("not great[.!?]*") ? null : facts.sleepQuality(),
                facts.shortnessOfBreath(), temperature));
    }

    static boolean nearSyncope(String text) {
        return text.matches("(?s).*\\b(?:almost|nearly)\\s+(?:fainted|passed out|lost consciousness)\\b.*")
                || text.matches("(?s).*\\b(?:might|may|could)\\s+(?:faint|pass out|lose consciousness)\\b.*");
    }

    public ExtractedClinicalFacts validate(ExtractedClinicalFacts facts) {
        return new ExtractedClinicalFacts(
                painScore(facts.painScore()),
                facts.dizziness(),
                blankToNull(facts.dizzinessOnset()),
                facts.lossOfConsciousness(),
                facts.medicationTaken(),
                vocabulary(facts.appetite(), APPETITE, "appetite"),
                vocabulary(facts.sleepQuality(), SLEEP, "sleepQuality"),
                facts.shortnessOfBreath(),
                temperature(facts.temperature())
        );
    }

    private Integer painScore(Integer painScore) {
        if (painScore == null) {
            return null;
        }
        if (painScore < 0 || painScore > 10) {
            log.warn("Rejected invalid pain score from model output");
            return null;
        }
        return painScore;
    }

    private Double temperature(Double temperature) {
        if (temperature == null) {
            return null;
        }
        if (!Double.isFinite(temperature) || temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
            log.warn("Rejected invalid temperature from model output");
            return null;
        }
        return temperature;
    }

    private String vocabulary(String value, Set<String> allowed, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        String canonical = normalized.toLowerCase(Locale.ROOT);
        if (!allowed.contains(canonical)) {
            log.warn("Rejected unsupported {} value from model output", field);
            return null;
        }
        return canonical;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
