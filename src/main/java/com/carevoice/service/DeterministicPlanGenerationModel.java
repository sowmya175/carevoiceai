package com.carevoice.service;
import com.carevoice.service.PlanGenerationModel.AllowedFieldInput;
import com.carevoice.service.PlanGenerationModel.ConditionInput;
import com.carevoice.service.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.service.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;

import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.MonitoringField;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Development generator used when plan AI is disabled.
 * It selects each routine demo field at most once.
 */
@Component
public class DeterministicPlanGenerationModel implements PlanGenerationModel {
    private static final List<String> ROUTINE = List.of(
            "PAIN_SCORE", "MEDICATION_TAKEN", "APPETITE", "SLEEP_QUALITY", "TEMPERATURE");

    @Override
    public PlanGenerationResult generate(PatientPlanGenerationContext context) {
        Set<MonitoringCategory> categories = new LinkedHashSet<>();
        for (ConditionInput condition : context.conditions()) {
            categories.add(condition.category());
        }
        List<GeneratedQuestion> matched = select(context, categories, false);
        List<GeneratedQuestion> questions = matched.isEmpty() ? select(context, categories, true) : matched;
        List<String> families = categories.stream().map(Enum::name).sorted().toList();
        return new PlanGenerationResult(families, questions);
    }

    private static List<GeneratedQuestion> select(
            PatientPlanGenerationContext context, Set<MonitoringCategory> categories, boolean includeAllRoutine) {
        List<GeneratedQuestion> questions = new ArrayList<>();
        for (String code : ROUTINE) {
            AllowedFieldInput field = context.allowedFields().stream()
                    .filter(candidate -> code.equals(candidate.code()))
                    .findFirst()
                    .orElse(null);
            if (field == null) {
                continue;
            }
            boolean overlaps = field.categories().stream().anyMatch(categories::contains);
            if (!includeAllRoutine && !overlaps) {
                continue;
            }
            List<Long> relevant = context.conditions().stream()
                    .filter(condition -> includeAllRoutine || field.categories().contains(condition.category()))
                    .map(ConditionInput::patientConditionId)
                    .toList();
            questions.add(new GeneratedQuestion(
                    code,
                    DemoMonitoringPlans.defaultQuestion(MonitoringField.valueOf(code)),
                    true,
                    relevant,
                    "Included because it overlaps an active monitoring category."));
        }
        return questions;
    }
}
