package com.carevoice.proposal;

import com.carevoice.domain.MonitoringAnswerType;
import com.carevoice.domain.MonitoringCategory;

import java.util.List;
import java.util.Set;

/**
 * The only planning dependency used by the rest of CareVoice.
 * Phase 6.5D replaces the temporary implementation with a fine-tuned planning model.
 */
public interface PlanGenerationModel {
    PlanGenerationResult generate(PatientPlanGenerationContext context);

    record PatientPlanGenerationContext(
            Long patientId,
            List<ConditionInput> conditions,
            ConditionInput primaryCondition,
            List<AllowedFieldInput> allowedFields,
            ExistingPlanInput existingPlan
    ) {}

    record ConditionInput(
            Long patientConditionId,
            String conditionName,
            MonitoringCategory category,
            boolean primary
    ) {}

    record AllowedFieldInput(
            String code,
            String displayName,
            String description,
            MonitoringAnswerType answerType,
            Double minimumValue,
            Double maximumValue,
            List<String> allowedValues,
            Set<MonitoringCategory> categories
    ) {}

    /**
     * Metadata about the assigned plan. It does not include a patient name or plan wording.
     */
    record ExistingPlanInput(boolean patientSpecific) {}

    record PlanGenerationResult(List<String> conditionFamilies, List<GeneratedQuestion> questions) {}

    record GeneratedQuestion(
            String fieldCode,
            String questionText,
            boolean required,
            List<Long> relevantConditionIds,
            String rationale
    ) {}
}
