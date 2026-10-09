package com.carevoice.training;

import com.carevoice.domain.MonitoringAnswerType;
import com.carevoice.domain.MonitoringCategory;

import java.util.List;

/**
 * Canonical plan-generation example. Property names inside {@code input} and {@code output}
 * match the Phase 6.5B generation contract. {@code review} is dataset metadata and is not model input.
 */
public record PlanExample(
        String id,
        String groupId,
        String datasetVersion,
        Review review,
        TrainingInput input,
        TrainingOutput output
) {
    public static final String DATASET_VERSION = "carevoice-plan-generation-v1";
    public static final String SCHEMA_VERSION = "1";

    public record Review(List<String> tags, boolean categoryMismatch) {}

    public record TrainingInput(
            List<TrainingCondition> conditions,
            List<TrainingField> allowedFields,
            TrainingExistingPlan existingPlan
    ) {}

    /** Same names as {@code PlanGenerationModel.ConditionInput}. Ids are example-local, not production ids. */
    public record TrainingCondition(
            long patientConditionId,
            String conditionName,
            MonitoringCategory category,
            boolean primary
    ) {}

    /** Same names as {@code PlanGenerationModel.AllowedFieldInput}. */
    public record TrainingField(
            String code,
            String displayName,
            String description,
            MonitoringAnswerType answerType,
            Double minimumValue,
            Double maximumValue,
            List<String> allowedValues,
            List<MonitoringCategory> categories
    ) {}

    /** Same names as {@code PlanGenerationModel.ExistingPlanInput}. */
    public record TrainingExistingPlan(boolean patientSpecific) {}

    /** Same names as {@code PlanGenerationModel.PlanGenerationResult}. */
    public record TrainingOutput(List<String> conditionFamilies, List<TrainingQuestion> questions) {}

    /** Same names as {@code PlanGenerationModel.GeneratedQuestion}. */
    public record TrainingQuestion(
            String fieldCode,
            String questionText,
            boolean required,
            List<Long> relevantConditionIds,
            String rationale
    ) {}
}
