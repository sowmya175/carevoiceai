package com.carevoice.training;

import com.carevoice.domain.MonitoringCategory;
import com.carevoice.training.PlanExample.TrainingCondition;
import com.carevoice.training.PlanExample.TrainingField;
import com.carevoice.training.PlanExample.TrainingQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlanDatasetValidator {
    private static final Set<String> TAGS = Set.of(
            "single-condition", "multi-condition", "primary-reversal", "category-mismatch",
            "dedupe", "sparse-catalog", "no-good-fit", "selective", "unsupported-omission", "paraphrase");

    private PlanDatasetValidator() {}

    public static List<String> problems(ObjectMapper mapper, List<PlanExample> examples) {
        List<String> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (PlanExample example : examples) {
            if (!ids.add(example.id())) {
                problems.add(example.id() + ": duplicate example id");
            }
            problems.addAll(problems(mapper, example));
        }
        return problems;
    }

    public static List<String> problems(ObjectMapper mapper, PlanExample example) {
        List<String> problems = new ArrayList<>();
        String id = example.id() == null ? "(missing id)" : example.id();
        if (example.id() == null || !example.id().matches("cv-plan-(\\d{6}|c-\\d{4})")) {
            problems.add(id + ": example id is not in the canonical format");
        }
        if (!PlanExample.DATASET_VERSION.equals(example.datasetVersion())) {
            problems.add(id + ": unexpected dataset version");
        }
        if (example.groupId() == null || example.groupId().isBlank()) {
            problems.add(id + ": missing split group");
        }
        if (example.review() == null || example.review().tags() == null) {
            problems.add(id + ": missing review tags");
        } else {
            for (String tag : example.review().tags()) {
                if (!TAGS.contains(tag)) {
                    problems.add(id + ": unknown review tag");
                }
            }
        }
        if (example.input() == null || example.input().conditions() == null || example.input().conditions().isEmpty()) {
            problems.add(id + ": at least one condition is required");
            return problems;
        }
        long primary = example.input().conditions().stream().filter(TrainingCondition::primary).count();
        if (primary != 1) {
            problems.add(id + ": exactly one primary condition is required");
        }
        Set<Long> conditionIds = new HashSet<>();
        for (TrainingCondition condition : example.input().conditions()) {
            if (condition.conditionName() == null || condition.conditionName().isBlank()) {
                problems.add(id + ": condition name is blank");
            }
            if (condition.category() == null) {
                problems.add(id + ": missing monitoring category");
            }
            if (!conditionIds.add(condition.patientConditionId())) {
                problems.add(id + ": duplicate condition id");
            }
        }
        if (example.input().allowedFields() == null || example.input().allowedFields().isEmpty()) {
            problems.add(id + ": allowed fields are required");
        } else {
            Set<String> codes = new HashSet<>();
            for (TrainingField field : example.input().allowedFields()) {
                if (!codes.add(field.code())) {
                    problems.add(id + ": duplicate allowed field");
                }
                if (!PlanTrainingCatalog.planAskableCodes().contains(field.code())) {
                    problems.add(id + ": allowed field is not plan-askable");
                } else if (!field.equals(PlanTrainingCatalog.definition(field.code()))) {
                    problems.add(id + ": allowed field metadata does not match the v1 catalog");
                }
            }
        }
        if (example.output() == null || example.output().questions() == null || example.output().conditionFamilies() == null) {
            problems.add(id + ": output is missing");
            return problems;
        }
        Set<String> families = new HashSet<>();
        for (String family : example.output().conditionFamilies()) {
            if (!PlanTrainingCatalog.CONDITION_FAMILIES.contains(family)) {
                problems.add(id + ": condition family is outside the controlled vocabulary");
            }
            if (!families.add(family)) {
                problems.add(id + ": duplicate condition family");
            }
        }
        if (example.output().conditionFamilies().isEmpty()) {
            problems.add(id + ": at least one condition family is required");
        }
        Set<String> allowed = new HashSet<>();
        if (example.input().allowedFields() != null) {
            for (TrainingField field : example.input().allowedFields()) {
                allowed.add(field.code());
            }
        }
        Set<String> used = new HashSet<>();
        int previousOrder = -1;
        for (TrainingQuestion question : example.output().questions()) {
            if (question.fieldCode() == null || !allowed.contains(question.fieldCode())) {
                problems.add(id + ": output field is not in allowedFields");
            }
            if (!PlanTrainingCatalog.ROUTINE_TARGET_FIELDS.contains(question.fieldCode())) {
                problems.add(id + ": output field is not a current routine training target");
            }
            if (question.fieldCode() != null && !used.add(question.fieldCode())) {
                problems.add(id + ": duplicate output field");
            }
            if (question.questionText() == null || question.questionText().isBlank()) {
                problems.add(id + ": question text is blank");
            }
            if (!question.required()) {
                problems.add(id + ": v1 routine questions are required");
            }
            if (question.relevantConditionIds() == null || question.relevantConditionIds().isEmpty()) {
                problems.add(id + ": relevant condition ids are required");
            } else if (!conditionIds.containsAll(question.relevantConditionIds())) {
                problems.add(id + ": relevant condition id is not in the input");
            }
            int order = PlanTrainingCatalog.ROUTINE_TARGET_FIELDS.indexOf(question.fieldCode());
            if (order < previousOrder) {
                problems.add(id + ": question order does not follow the routine priority");
            }
            previousOrder = order;
            problems.addAll(PlanWordingChecker.findings(id, question.fieldCode(), question.questionText()));
        }
        if (example.input().existingPlan() == null) {
            problems.add(id + ": existing plan metadata is required");
        }
        problems.addAll(PlanPhiScanner.findings(mapper, example));
        if (example.input().conditions().stream().anyMatch(condition -> condition.category() == null)) {
            return problems;
        }
        for (TrainingCondition condition : example.input().conditions()) {
            if (!isCategory(condition.category())) {
                problems.add(id + ": category is not a MonitoringCategory");
            }
        }
        return problems;
    }

    private static boolean isCategory(MonitoringCategory category) {
        return category != null;
    }
}
