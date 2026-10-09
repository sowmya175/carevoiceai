package com.carevoice.proposal;

import com.carevoice.proposal.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlanGenerationJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PlanGenerationJson() {}

    public static PlanGenerationResult parse(String json) {
        if (json == null || json.isBlank()) {
            throw new PlanGenerationFailedException();
        }
        try {
            Payload payload = MAPPER.readValue(json, Payload.class);
            List<GeneratedQuestion> questions = new ArrayList<>();
            if (payload.questions != null) {
                for (QuestionPayload question : payload.questions) {
                    questions.add(new GeneratedQuestion(
                            question.fieldCode,
                            question.questionText,
                            question.required == null || question.required,
                            question.relevantConditionIds == null ? List.of() : question.relevantConditionIds,
                            question.rationale));
                }
            }
            return new PlanGenerationResult(
                    payload.conditionFamilies == null ? List.of() : payload.conditionFamilies,
                    questions);
        } catch (JsonProcessingException ex) {
            throw new PlanGenerationFailedException();
        }
    }

    static Schema responseSchema() {
        Map<String, Schema> questionProperties = new LinkedHashMap<>();
        questionProperties.put("fieldCode", Schema.builder().type(Type.Known.STRING)
                .description("A field code from the allowed list.").build());
        questionProperties.put("questionText", Schema.builder().type(Type.Known.STRING)
                .description("Neutral daily monitoring question.").build());
        questionProperties.put("required", Schema.builder().type(Type.Known.BOOLEAN)
                .description("Whether the daily check-in must ask this question.").build());
        questionProperties.put("relevantConditionIds", Schema.builder().type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.INTEGER).build())
                .description("Condition ids this question relates to.").build());
        questionProperties.put("rationale", Schema.builder().type(Type.Known.STRING)
                .description("Short reason for including the field. Not a diagnosis.").build());
        Schema question = Schema.builder().type(Type.Known.OBJECT)
                .properties(questionProperties)
                .required(List.copyOf(questionProperties.keySet()))
                .propertyOrdering(List.copyOf(questionProperties.keySet()))
                .build();
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("conditionFamilies", Schema.builder().type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.STRING).build())
                .description("Descriptive labels. Not diagnoses or billing codes.").build());
        properties.put("questions", Schema.builder().type(Type.Known.ARRAY).items(question).build());
        List<String> names = List.copyOf(properties.keySet());
        return Schema.builder().type(Type.Known.OBJECT)
                .properties(properties).required(names).propertyOrdering(names).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Payload {
        public List<String> conditionFamilies;
        public List<QuestionPayload> questions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class QuestionPayload {
        public String fieldCode;
        public String questionText;
        public Boolean required;
        public List<Long> relevantConditionIds;
        public String rationale;
    }
}
