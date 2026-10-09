package com.carevoice.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExtractedFactsJson {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ExtractedFactsJson() {}

    public static String write(ExtractedClinicalFacts facts) {
        return MAPPER.writeValueAsString(nonNullFields(facts));
    }

    public static Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(json, MAP_TYPE);
    }

    static Map<String, Object> nonNullFields(ExtractedClinicalFacts facts) {
        Map<String, Object> fields = new LinkedHashMap<>();
        put(fields, "painScore", facts.painScore());
        put(fields, "dizziness", facts.dizziness());
        put(fields, "dizzinessOnset", facts.dizzinessOnset());
        put(fields, "lossOfConsciousness", facts.lossOfConsciousness());
        put(fields, "medicationTaken", facts.medicationTaken());
        put(fields, "appetite", facts.appetite());
        put(fields, "sleepQuality", facts.sleepQuality());
        put(fields, "shortnessOfBreath", facts.shortnessOfBreath());
        put(fields, "temperature", facts.temperature());
        return fields;
    }

    private static void put(Map<String, Object> fields, String name, Object value) {
        if (value != null) {
            fields.put(name, value);
        }
    }
}
