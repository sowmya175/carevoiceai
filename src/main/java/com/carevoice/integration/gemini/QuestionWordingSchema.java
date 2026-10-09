package com.carevoice.integration.gemini;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QuestionWordingSchema {
    private QuestionWordingSchema() {}

    static Schema responseSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("question", Schema.builder()
                .type(Type.Known.STRING)
                .description("One short patient-facing question about the required field.")
                .build());
        List<String> names = List.copyOf(properties.keySet());
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .description("The single question to show the patient.")
                .properties(properties)
                .required(names)
                .propertyOrdering(names)
                .build();
    }
}
