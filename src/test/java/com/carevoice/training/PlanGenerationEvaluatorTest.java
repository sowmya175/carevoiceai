package com.carevoice.training;

import com.carevoice.proposal.DeterministicPlanGenerationModel;
import com.carevoice.proposal.PlanGenerationModel;
import com.carevoice.proposal.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.training.PlanGenerationEvaluator.EvaluationReport;
import com.carevoice.training.PlanTrainingCorpus.Dataset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanGenerationEvaluatorTest {
    @Test
    void metricsScoreFieldSelectionWithoutRequiringExactWording() {
        Dataset dataset = PlanTrainingCorpus.build();
        PlanExample example = dataset.canonical().stream()
                .filter(candidate -> candidate.output().questions().size() >= 2)
                .findFirst().orElseThrow();
        List<String> gold = example.output().questions().stream().map(question -> question.fieldCode()).toList();
        String first = gold.getFirst();
        EvaluationReport perfect = PlanGenerationEvaluator.evaluate(model(example, gold), List.of(example));
        assertThat(perfect.fieldPrecision()).isEqualTo(1.0);
        assertThat(perfect.fieldRecall()).isEqualTo(1.0);
        assertThat(perfect.fieldF1()).isEqualTo(1.0);
        assertThat(perfect.duplicateFieldRate()).isZero();
        EvaluationReport extra = PlanGenerationEvaluator.evaluate(
                model(example, List.of(first, "BLOOD_PRESSURE")), List.of(example));
        assertThat(extra.fieldPrecision()).isEqualTo(0.5);
        assertThat(extra.unknownFieldRate()).isEqualTo(1.0);
        assertThat(extra.unsupportedFieldRate()).isEqualTo(1.0);
        EvaluationReport duplicate = PlanGenerationEvaluator.evaluate(
                model(example, List.of(first, first)), List.of(example));
        assertThat(duplicate.duplicateFieldRate()).isEqualTo(1.0);
    }

    @Test
    void deterministicBaselineIsStructuredAndDoesNotCallAProvider() {
        Dataset dataset = PlanTrainingCorpus.build();
        List<PlanExample> test = dataset.canonical().stream()
                .filter(example -> dataset.splits().test().contains(example.id()))
                .toList();
        EvaluationReport report = PlanGenerationEvaluator.evaluate(new DeterministicPlanGenerationModel()::generate, test);
        assertThat(report.examples()).isEqualTo(test.size());
        assertThat(report.structuredOutputValidityRate()).isEqualTo(1.0);
        assertThat(report.duplicateFieldRate()).isZero();
        assertThat(report.unknownFieldRate()).isZero();
        assertThat(report.unsupportedFieldRate()).isZero();
        assertThat(report.fieldF1()).isBetween(0.0, 1.0);
        assertThat(report.wordingSafetyRate()).isEqualTo(1.0);
    }

    private static PlanGenerationModel model(PlanExample example, List<String> fieldCodes) {
        return context -> new PlanGenerationResult(
                example.output().conditionFamilies(),
                fieldCodes.stream().map(code -> new GeneratedQuestion(
                        code, question(code), true, List.of(1L), "Included as a daily monitoring question from the supplied field list."))
                        .toList());
    }

    private static String question(String code) {
        return switch (code) {
            case "PAIN_SCORE" -> "How would you rate your pain today from 0 to 10?";
            case "MEDICATION_TAKEN" -> "Have you taken your prescribed medications today?";
            case "APPETITE" -> "How has your appetite been today?";
            case "SLEEP_QUALITY" -> "How did you sleep last night?";
            case "TEMPERATURE" -> "What is your temperature reading?";
            default -> "This is a neutral daily question.";
        };
    }
}
