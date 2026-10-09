package com.carevoice.training;
import com.carevoice.training.PlanExample.TrainingCondition;
import com.carevoice.training.PlanExample.TrainingField;
import com.carevoice.training.PlanExample.TrainingQuestion;

import com.carevoice.service.PlanGenerationModel;
import com.carevoice.service.PlanGenerationModel.AllowedFieldInput;
import com.carevoice.service.PlanGenerationModel.ConditionInput;
import com.carevoice.service.PlanGenerationModel.ExistingPlanInput;
import com.carevoice.service.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.service.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Provider-neutral comparison of a {@link PlanGenerationModel} with curated targets.
 * Field selection is the primary score. Exact question wording is not required.
 */
public final class PlanGenerationEvaluator {
    private PlanGenerationEvaluator() {}

    public static EvaluationReport evaluate(PlanGenerationModel model, List<PlanExample> examples) {
        int truePositive = 0;
        int predicted = 0;
        int expected = 0;
        int duplicateExamples = 0;
        int unknownExamples = 0;
        int unsupportedExamples = 0;
        int structuredValid = 0;
        int familyMatches = 0;
        int requiredCorrect = 0;
        int requiredCompared = 0;
        int wordingSafe = 0;
        for (PlanExample example : examples) {
            Score score = score(model, example);
            truePositive += score.truePositive;
            predicted += score.predicted;
            expected += score.expected;
            if (score.duplicate) {
                duplicateExamples++;
            }
            if (score.unknown) {
                unknownExamples++;
            }
            if (score.unsupported) {
                unsupportedExamples++;
            }
            if (score.structuredValid) {
                structuredValid++;
            }
            if (score.familyMatch) {
                familyMatches++;
            }
            requiredCorrect += score.requiredCorrect;
            requiredCompared += score.requiredCompared;
            if (score.wordingSafe) {
                wordingSafe++;
            }
        }
        int total = examples.size();
        return new EvaluationReport(
                total,
                ratio(truePositive, predicted),
                ratio(truePositive, expected),
                f1(ratio(truePositive, predicted), ratio(truePositive, expected)),
                rate(duplicateExamples, total),
                rate(unknownExamples, total),
                rate(unsupportedExamples, total),
                rate(structuredValid, total),
                rate(familyMatches, total),
                requiredCompared == 0 ? 1.0 : (double) requiredCorrect / requiredCompared,
                rate(wordingSafe, total));
    }

    private static Score score(PlanGenerationModel model, PlanExample example) {
        PlanGenerationResult result;
        int goldCount = example.output().questions().size();
        try {
            result = model.generate(context(example));
        } catch (RuntimeException ex) {
            return Score.invalid(goldCount);
        }
        if (result == null || result.questions() == null || result.conditionFamilies() == null) {
            return Score.invalid(goldCount);
        }
        boolean structured = true;
        Set<String> seen = new LinkedHashSet<>();
        boolean duplicate = false;
        boolean unknown = false;
        boolean unsupported = false;
        Set<String> allowed = new HashSet<>();
        for (TrainingField field : example.input().allowedFields()) {
            allowed.add(field.code());
        }
        Set<String> predicted = new LinkedHashSet<>();
        boolean wordingSafe = true;
        for (GeneratedQuestion question : result.questions()) {
            if (question.fieldCode() == null || question.fieldCode().isBlank()
                    || question.questionText() == null || question.questionText().isBlank()) {
                structured = false;
            }
            String code = question.fieldCode() == null ? "" : question.fieldCode();
            if (!seen.add(code)) {
                duplicate = true;
            }
            if (!allowed.contains(code)) {
                unknown = true;
            }
            if (PlanTrainingCatalog.NOT_PLAN_ASKABLE.contains(code) || PlanTrainingCatalog.FOLLOW_UP_FIELDS.contains(code)) {
                unsupported = true;
            }
            predicted.add(code);
            if (!PlanWordingChecker.findings(example.id(), code, question.questionText()).isEmpty()) {
                wordingSafe = false;
            }
        }
        Set<String> gold = new LinkedHashSet<>();
        for (TrainingQuestion question : example.output().questions()) {
            gold.add(question.fieldCode());
        }
        int truePositive = 0;
        int requiredCorrect = 0;
        int requiredCompared = 0;
        for (String code : predicted) {
            if (gold.contains(code)) {
                truePositive++;
                boolean predictedRequired = result.questions().stream()
                        .filter(question -> code.equals(question.fieldCode()))
                        .findFirst().orElseThrow().required();
                boolean expectedRequired = example.output().questions().stream()
                        .filter(question -> code.equals(question.fieldCode()))
                        .findFirst().orElseThrow().required();
                requiredCompared++;
                if (predictedRequired == expectedRequired) {
                    requiredCorrect++;
                }
            }
        }
        boolean familyMatch = new HashSet<>(result.conditionFamilies())
                .equals(new HashSet<>(example.output().conditionFamilies()));
        return new Score(truePositive, predicted.size(), gold.size(), duplicate, unknown, unsupported,
                structured, familyMatch, requiredCorrect, requiredCompared, wordingSafe);
    }

    static PatientPlanGenerationContext context(PlanExample example) {
        List<ConditionInput> conditions = example.input().conditions().stream()
                .map(PlanGenerationEvaluator::condition)
                .toList();
        ConditionInput primary = conditions.stream().filter(ConditionInput::primary).findFirst().orElse(null);
        List<AllowedFieldInput> fields = example.input().allowedFields().stream()
                .map(PlanGenerationEvaluator::field)
                .toList();
        boolean patientSpecific = example.input().existingPlan() != null && example.input().existingPlan().patientSpecific();
        return new PatientPlanGenerationContext(null, conditions, primary, fields, new ExistingPlanInput(patientSpecific));
    }

    private static ConditionInput condition(TrainingCondition condition) {
        return new ConditionInput(
                condition.patientConditionId(), condition.conditionName(), condition.category(), condition.primary());
    }

    private static AllowedFieldInput field(TrainingField field) {
        return new AllowedFieldInput(
                field.code(), field.displayName(), field.description(), field.answerType(),
                field.minimumValue(), field.maximumValue(), field.allowedValues(),
                Set.copyOf(field.categories()));
    }

    private static double ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return numerator == 0 ? 1.0 : 0.0;
        }
        return (double) numerator / denominator;
    }

    private static double rate(int count, int total) {
        return total == 0 ? 0.0 : (double) count / total;
    }

    private static double f1(double precision, double recall) {
        if (precision + recall == 0) {
            return 0.0;
        }
        return 2 * precision * recall / (precision + recall);
    }

    private record Score(
            int truePositive,
            int predicted,
            int expected,
            boolean duplicate,
            boolean unknown,
            boolean unsupported,
            boolean structuredValid,
            boolean familyMatch,
            int requiredCorrect,
            int requiredCompared,
            boolean wordingSafe
    ) {
        static Score invalid(int expected) {
            return new Score(0, 0, expected, false, false, false, false, false, 0, 0, false);
        }
    }

    public record EvaluationReport(
            int examples,
            double fieldPrecision,
            double fieldRecall,
            double fieldF1,
            double duplicateFieldRate,
            double unknownFieldRate,
            double unsupportedFieldRate,
            double structuredOutputValidityRate,
            double conditionFamilyAccuracy,
            double requiredFlagAccuracy,
            double wordingSafetyRate
    ) {}
}
