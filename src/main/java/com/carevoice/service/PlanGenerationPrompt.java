package com.carevoice.service;
import com.carevoice.service.PlanGenerationModel.AllowedFieldInput;
import com.carevoice.service.PlanGenerationModel.ConditionInput;
import com.carevoice.service.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.service.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;

import com.carevoice.domain.MonitoringCategory;

import com.carevoice.training.PlanExample.TrainingCondition;
import com.carevoice.training.PlanExample.TrainingField;
import com.carevoice.training.PlanExample.TrainingInput;
import com.carevoice.training.PlanExample.TrainingOutput;
import com.carevoice.training.PlanExample.TrainingQuestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User and model text for plan tuning and plan inference.
 * The payload has conditions, allowed fields, and existing-plan metadata only.
 */
public final class PlanGenerationPrompt {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PlanGenerationPrompt() {}

    public static String userText(PatientPlanGenerationContext context) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (ConditionInput condition : context.conditions()) {
            conditions.add(conditionMap(
                    condition.patientConditionId(),
                    condition.conditionName(),
                    condition.category().name(),
                    condition.primary()));
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (AllowedFieldInput field : context.allowedFields()) {
            List<String> categories = field.categories().stream().map(Enum::name).sorted().toList();
            fields.add(fieldMap(
                    field.code(), field.displayName(), field.description(), field.answerType().name(),
                    field.minimumValue(), field.maximumValue(), field.allowedValues(), categories));
        }
        boolean patientSpecific = context.existingPlan() != null && context.existingPlan().patientSpecific();
        return write(userMap(conditions, fields, patientSpecific));
    }

    public static String userText(TrainingInput input) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (TrainingCondition condition : input.conditions()) {
            conditions.add(conditionMap(
                    condition.patientConditionId(),
                    condition.conditionName(),
                    condition.category().name(),
                    condition.primary()));
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (TrainingField field : input.allowedFields()) {
            List<String> categories = field.categories().stream()
                    .sorted(Comparator.comparing(MonitoringCategory::name))
                    .map(MonitoringCategory::name)
                    .toList();
            fields.add(fieldMap(
                    field.code(), field.displayName(), field.description(), field.answerType().name(),
                    field.minimumValue(), field.maximumValue(), field.allowedValues(), categories));
        }
        boolean patientSpecific = input.existingPlan() != null && input.existingPlan().patientSpecific();
        return write(userMap(conditions, fields, patientSpecific));
    }

    public static String outputText(TrainingOutput output) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (TrainingQuestion question : output.questions()) {
            questions.add(questionMap(
                    question.fieldCode(), question.questionText(), question.required(),
                    question.relevantConditionIds(), question.rationale()));
        }
        return write(outputMap(output.conditionFamilies(), questions));
    }

    public static String outputText(PlanGenerationResult result) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (GeneratedQuestion question : result.questions()) {
            questions.add(questionMap(
                    question.fieldCode(), question.questionText(), question.required(),
                    question.relevantConditionIds(), question.rationale()));
        }
        return write(outputMap(result.conditionFamilies(), questions));
    }

    private static Map<String, Object> userMap(
            List<Map<String, Object>> conditions, List<Map<String, Object>> fields, boolean patientSpecific) {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("patientSpecific", patientSpecific);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conditions", conditions);
        body.put("allowedFields", fields);
        body.put("existingPlan", existing);
        return body;
    }

    private static Map<String, Object> conditionMap(Long id, String name, String category, boolean primary) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patientConditionId", id);
        row.put("conditionName", name);
        row.put("category", category);
        row.put("primary", primary);
        return row;
    }

    private static Map<String, Object> fieldMap(
            String code, String displayName, String description, String answerType,
            Double minimumValue, Double maximumValue, List<String> allowedValues, List<String> categories) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("displayName", displayName);
        row.put("description", description);
        row.put("answerType", answerType);
        row.put("minimumValue", minimumValue);
        row.put("maximumValue", maximumValue);
        row.put("allowedValues", allowedValues == null ? List.of() : allowedValues);
        row.put("categories", categories);
        return row;
    }

    private static Map<String, Object> questionMap(
            String fieldCode, String questionText, boolean required, List<Long> relevantConditionIds, String rationale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fieldCode", fieldCode);
        row.put("questionText", questionText);
        row.put("required", required);
        row.put("relevantConditionIds", relevantConditionIds == null ? List.of() : relevantConditionIds);
        row.put("rationale", rationale);
        return row;
    }

    private static Map<String, Object> outputMap(List<String> families, List<Map<String, Object>> questions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conditionFamilies", families == null ? List.of() : families);
        body.put("questions", questions);
        return body;
    }

    private static String write(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write the plan generation prompt.");
        }
    }
}
