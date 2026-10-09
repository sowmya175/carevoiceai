package com.carevoice.service;
import com.carevoice.domain.Patient;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DeterministicClinicalNoteService implements ClinicalNoteService {
    static final String NO_FACTS = "No new clinical facts were identified from this response.";

    @Override
    public String write(String question, String patientResponse, ExtractedClinicalFacts facts) {
        List<String> phrases = phrases(facts);
        if (phrases.isEmpty()) {
            return NO_FACTS;
        }
        return "Patient reports " + join(phrases) + ".";
    }

    @Override
    public String provider() {
        return "deterministic";
    }

    private static List<String> phrases(ExtractedClinicalFacts facts) {
        List<String> phrases = new ArrayList<>();
        if (facts.dizziness() != null) {
            phrases.add(facts.dizziness() ? "dizziness" : "no dizziness");
        }
        if (facts.dizzinessOnset() != null) {
            phrases.add("dizziness onset " + facts.dizzinessOnset());
        }
        if (facts.lossOfConsciousness() != null) {
            phrases.add(facts.lossOfConsciousness() ? "loss of consciousness" : "no loss of consciousness");
        }
        if (facts.shortnessOfBreath() != null) {
            phrases.add(facts.shortnessOfBreath() ? "shortness of breath" : "no shortness of breath");
        }
        if (facts.painScore() != null) {
            phrases.add("pain rated " + facts.painScore() + "/10");
        }
        if (facts.medicationTaken() != null) {
            phrases.add(facts.medicationTaken() ? "medication was taken" : "medication was not taken");
        }
        if (facts.appetite() != null) {
            phrases.add("appetite " + facts.appetite());
        }
        if (facts.sleepQuality() != null) {
            phrases.add("sleep " + facts.sleepQuality());
        }
        if (facts.temperature() != null) {
            phrases.add("temperature " + facts.temperature());
        }
        return phrases;
    }

    private static String join(List<String> phrases) {
        if (phrases.size() == 1) {
            return phrases.get(0);
        }
        if (phrases.size() == 2) {
            return phrases.get(0) + " and " + phrases.get(1);
        }
        return String.join(", ", phrases.subList(0, phrases.size() - 1))
                + ", and "
                + phrases.get(phrases.size() - 1);
    }

    static String fromStoredFacts(Map<String, Object> facts) {
        return new DeterministicClinicalNoteService().write(null, null, toFacts(facts));
    }

    private static ExtractedClinicalFacts toFacts(Map<String, Object> facts) {
        return new ExtractedClinicalFacts(
                integer(facts.get("painScore")),
                bool(facts.get("dizziness")),
                text(facts.get("dizzinessOnset")),
                bool(facts.get("lossOfConsciousness")),
                bool(facts.get("medicationTaken")),
                text(facts.get("appetite")),
                text(facts.get("sleepQuality")),
                bool(facts.get("shortnessOfBreath")),
                decimal(facts.get("temperature"))
        );
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static Double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
