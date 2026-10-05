package com.carevoice.agent;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClinicalExtractionSchema {
    private ClinicalExtractionSchema() {}

    static Schema responseSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("painScore", field(Type.Known.INTEGER,
                "Integer pain score from 0 through 10 if explicitly stated. Null if not mentioned."));
        properties.put("dizziness", field(Type.Known.BOOLEAN,
                "True if the patient currently reports dizziness or lightheadedness. False if explicitly denied. Null if not mentioned."));
        properties.put("dizzinessOnset", field(Type.Known.STRING,
                "When the dizziness started, in the patient's words, if stated or answered. Null otherwise."));
        properties.put("lossOfConsciousness", field(Type.Known.BOOLEAN,
                "True if the patient fainted, passed out, or lost consciousness. False if explicitly denied. Null if not mentioned."));
        properties.put("medicationTaken", field(Type.Known.BOOLEAN,
                "True if prescribed medication was taken. False if missed or explicitly denied. Null if not mentioned."));
        properties.put("appetite", field(Type.Known.STRING,
                "Appetite using only good, normal, reduced, or poor. Null if not mentioned."));
        properties.put("sleepQuality", field(Type.Known.STRING,
                "Sleep quality using only good, normal, or poor. Null if not mentioned."));
        properties.put("shortnessOfBreath", field(Type.Known.BOOLEAN,
                "True if the patient reports trouble breathing or shortness of breath. False if explicitly denied. Null if not mentioned."));
        properties.put("temperature", field(Type.Known.NUMBER,
                "Numeric temperature exactly as stated, without unit conversion. Null if not mentioned."));

        List<String> names = List.copyOf(properties.keySet());
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .description("Monitoring facts explicitly supported by the current patient message and the immediate question context.")
                .properties(properties)
                .required(names)
                .propertyOrdering(names)
                .build();
    }

    private static Schema field(Type.Known type, String description) {
        return Schema.builder()
                .type(type)
                .nullable(true)
                .description(description)
                .build();
    }
}
