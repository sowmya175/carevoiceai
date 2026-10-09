package com.carevoice.training;
import com.carevoice.training.PlanExample.TrainingQuestion;

import com.carevoice.domain.MonitoringCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Curated v1 selection rule. This is not a clinical protocol.
 * Fields are the union of per-category routine fields, deduplicated, then ordered by a fixed priority.
 * Temperature is included only when a supplied condition is explicitly marked for that daily reading.
 * Follow-up safety fields are never routine targets.
 */
public final class PlanSelectionPolicy {
    public static final String QUESTION_RATIONALE = "Included as a daily monitoring question from the supplied field list.";

    private static final Map<MonitoringCategory, List<String>> BY_CATEGORY = Map.of(
            MonitoringCategory.POST_OPERATIVE, List.of("PAIN_SCORE", "MEDICATION_TAKEN", "APPETITE", "SLEEP_QUALITY"),
            MonitoringCategory.HYPERTENSION, List.of("MEDICATION_TAKEN"),
            MonitoringCategory.DIABETES, List.of("MEDICATION_TAKEN", "APPETITE"),
            MonitoringCategory.CARDIAC, List.of("MEDICATION_TAKEN"),
            MonitoringCategory.RESPIRATORY, List.of("MEDICATION_TAKEN"),
            MonitoringCategory.WELLNESS, List.of("PAIN_SCORE", "APPETITE", "SLEEP_QUALITY"),
            MonitoringCategory.OTHER, List.of("MEDICATION_TAKEN"));

    private static final Map<String, String> WORDING = Map.of(
            "PAIN_SCORE", "How would you rate your pain today from 0 to 10?",
            "MEDICATION_TAKEN", "Have you taken your prescribed medications today?",
            "APPETITE", "How has your appetite been today?",
            "SLEEP_QUALITY", "How did you sleep last night?",
            "TEMPERATURE", "What is your temperature reading?");

    private PlanSelectionPolicy() {}

    public static Selection select(List<DraftCondition> conditions, Set<String> allowed, boolean categoryMismatch) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        Map<String, List<Long>> relevant = new LinkedHashMap<>();
        List<String> families = new ArrayList<>();
        long id = 1;
        for (DraftCondition condition : conditions) {
            String family = categoryMismatch ? "OTHER" : condition.family();
            if (!families.contains(family)) {
                families.add(family);
            }
            List<String> contributed = new ArrayList<>(BY_CATEGORY.get(condition.category()));
            if (condition.includeTemperature()
                    && (condition.category() == MonitoringCategory.POST_OPERATIVE
                    || condition.category() == MonitoringCategory.WELLNESS)) {
                contributed.add("TEMPERATURE");
            }
            for (String field : contributed) {
                if (!allowed.contains(field) || !PlanTrainingCatalog.ROUTINE_TARGET_FIELDS.contains(field)) {
                    continue;
                }
                selected.add(field);
                relevant.computeIfAbsent(field, ignored -> new ArrayList<>()).add(id);
            }
            id++;
        }
        List<TrainingQuestion> questions = new ArrayList<>();
        for (String field : PlanTrainingCatalog.ROUTINE_TARGET_FIELDS) {
            if (!selected.contains(field)) {
                continue;
            }
            questions.add(new TrainingQuestion(
                    field, WORDING.get(field), true, List.copyOf(relevant.get(field)), QUESTION_RATIONALE));
        }
        return new Selection(List.copyOf(families), List.copyOf(questions));
    }

    public record DraftCondition(
            String conditionName,
            MonitoringCategory category,
            String family,
            boolean includeTemperature
    ) {}

    public record Selection(List<String> conditionFamilies, List<TrainingQuestion> questions) {}
}
