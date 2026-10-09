package com.carevoice.clinician;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.history.ExtractedFactsJson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Clinician-facing labels for facts already stored on a session or a turn note.
 * A null value is omitted. It is not shown as No.
 */
public final class ClinicianFacts {
    private ClinicianFacts() {}

    public static List<ClinicianFact> fromSession(MonitoringSession session) {
        List<ClinicianFact> facts = new ArrayList<>();
        addNumber(facts, "Pain score", session.getPainScore(), true);
        addBoolean(facts, "Medication taken", session.getMedicationTaken());
        addText(facts, "Appetite", session.getAppetite());
        addText(facts, "Sleep", session.getSleepQuality());
        addBoolean(facts, "Dizziness", session.getDizziness());
        addText(facts, "Dizziness onset", session.getDizzinessOnset());
        addBoolean(facts, "Loss of consciousness", session.getLossOfConsciousness());
        addBoolean(facts, "Shortness of breath", session.getShortnessOfBreath());
        if (session.getTemperature() != null) {
            facts.add(new ClinicianFact("Temperature", plainNumber(session.getTemperature())));
        }
        return List.copyOf(facts);
    }

    public static List<ClinicianFact> fromTurn(String extractedFactsJson) {
        return fromMap(ExtractedFactsJson.read(extractedFactsJson));
    }

    static List<ClinicianFact> fromMap(Map<String, Object> fields) {
        List<ClinicianFact> facts = new ArrayList<>();
        addNumber(facts, "Pain score", fields.get("painScore"), true);
        addBoolean(facts, "Medication taken", fields.get("medicationTaken"));
        addText(facts, "Appetite", fields.get("appetite"));
        addText(facts, "Sleep", fields.get("sleepQuality"));
        addBoolean(facts, "Dizziness", fields.get("dizziness"));
        addText(facts, "Dizziness onset", fields.get("dizzinessOnset"));
        addBoolean(facts, "Loss of consciousness", fields.get("lossOfConsciousness"));
        addBoolean(facts, "Shortness of breath", fields.get("shortnessOfBreath"));
        addNumber(facts, "Temperature", fields.get("temperature"), false);
        return List.copyOf(facts);
    }

    private static void addText(List<ClinicianFact> facts, String label, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            facts.add(new ClinicianFact(label, text));
        }
    }

    private static void addBoolean(List<ClinicianFact> facts, String label, Object value) {
        if (value instanceof Boolean reported) {
            facts.add(new ClinicianFact(label, reported ? "Yes" : "No"));
        }
    }

    private static void addNumber(List<ClinicianFact> facts, String label, Object value, boolean pain) {
        if (!(value instanceof Number number)) {
            return;
        }
        String shown = pain
                ? number.intValue() + " / 10"
                : plainNumber(number.doubleValue());
        facts.add(new ClinicianFact(label, shown));
    }

    private static String plainNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public record ClinicianFact(String label, String value) {}
}
