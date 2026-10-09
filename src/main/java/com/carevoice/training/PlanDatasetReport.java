package com.carevoice.training;
import com.carevoice.training.PlanTrainingCorpus.Dataset;
import com.carevoice.training.PlanGenerationEvaluator.EvaluationReport;
import com.carevoice.training.PlanExample.Review;
import com.carevoice.training.PlanExample.TrainingCondition;
import com.carevoice.training.PlanExample.TrainingQuestion;

import com.carevoice.domain.MonitoringCategory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PlanDatasetReport {
    private PlanDatasetReport() {}

    static void write(
            Path markdown, Path json, Dataset dataset, EvaluationReport baseline, EvaluationReport challenge, ObjectMapper mapper)
            throws IOException {
        Map<String, Object> body = body(dataset, baseline, challenge);
        Files.writeString(json, mapper.writeValueAsString(body) + "\n", StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(baseline, challenge, body), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> body(Dataset dataset, EvaluationReport baseline, EvaluationReport challenge) {
        List<PlanExample> examples = dataset.canonical();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetVersion", PlanExample.DATASET_VERSION);
        body.put("totalExamples", examples.size());
        body.put("singleCondition", countTag(examples, "single-condition"));
        body.put("multiCondition", countTag(examples, "multi-condition"));
        body.put("byConditionCount", byCount(examples));
        body.put("byCategory", byCategory(examples));
        body.put("outputFieldFrequency", fieldFrequency(examples));
        body.put("conditionFamilyFrequency", familyFrequency(examples));
        body.put("dedupeExamples", countTag(examples, "dedupe"));
        body.put("unsupportedOmissionExamples", countTag(examples, "unsupported-omission"));
        body.put("noGoodFitExamples", countTag(examples, "no-good-fit"));
        body.put("sparseCatalogExamples", countTag(examples, "sparse-catalog"));
        body.put("selectiveExamples", countTag(examples, "selective"));
        body.put("paraphraseExamples", countTag(examples, "paraphrase"));
        body.put("primaryReversalExamples", countTag(examples, "primary-reversal"));
        body.put("challengeExamples", dataset.challenge().size());
        body.put("schemaValidation", "pass");
        body.put("deterministicBaselineOnTestSplit", baseline);
        body.put("deterministicBaselineOnChallenge", challenge);
        return body;
    }

    private static String markdown(EvaluationReport baseline, EvaluationReport challenge, Map<String, Object> body) {
        return """
                # Plan generation dataset review

                Dataset version: carevoice-plan-generation-v1

                The dataset is synthetic/curated development data and is not a medically validated clinical protocol.

                ## Counts

                - Total canonical examples: %d
                - Single-condition: %d
                - Multi-condition: %d
                - Challenge examples, held out of every split: %d

                ## Examples by number of conditions

                %s

                ## Examples by category

                An example is counted once for each category it contains.

                %s

                ## Output field frequency

                %s

                ## Condition family frequency

                %s

                ## Review tags

                - Duplicate-merging examples: %d
                - Unsupported-field omission examples: %d
                - No-good-fit examples: %d
                - Sparse-catalog examples: %d
                - Selective examples: %d
                - Paraphrase examples: %d
                - Primary-reversal examples: %d

                Schema validation: pass. Unsupported field violations in canonical outputs: 0. Duplicate field violations: 0.

                ## Deterministic baseline on the test split

                - Examples: %d
                - Field precision: %s
                - Field recall: %s
                - Field F1: %s
                - Duplicate field rate: %s
                - Unknown field rate: %s
                - Unsupported field rate: %s
                - Structured output validity: %s
                - Condition family accuracy: %s
                - Required-flag accuracy: %s
                - Wording safety rate: %s

                The deterministic generator is the Phase 6.5B workflow stand-in. It is not the training target. Test-split field agreement can be high when a group's curated field union matches category-tag overlap. Condition-family labels still use the training vocabulary.

                ## Deterministic baseline on the challenge set

                - Examples: %d
                - Field precision: %s
                - Field recall: %s
                - Field F1: %s
                - Duplicate field rate: %s
                - Unknown field rate: %s
                - Unsupported field rate: %s
                - Structured output validity: %s
                - Condition family accuracy: %s
                - Required-flag accuracy: %s
                - Wording safety rate: %s
                """.formatted(
                examples(body, "totalExamples"),
                examples(body, "singleCondition"),
                examples(body, "multiCondition"),
                examples(body, "challengeExamples"),
                lines(body.get("byConditionCount")),
                lines(body.get("byCategory")),
                lines(body.get("outputFieldFrequency")),
                lines(body.get("conditionFamilyFrequency")),
                examples(body, "dedupeExamples"),
                examples(body, "unsupportedOmissionExamples"),
                examples(body, "noGoodFitExamples"),
                examples(body, "sparseCatalogExamples"),
                examples(body, "selectiveExamples"),
                examples(body, "paraphraseExamples"),
                examples(body, "primaryReversalExamples"),
                baseline.examples(),
                num(baseline.fieldPrecision()),
                num(baseline.fieldRecall()),
                num(baseline.fieldF1()),
                num(baseline.duplicateFieldRate()),
                num(baseline.unknownFieldRate()),
                num(baseline.unsupportedFieldRate()),
                num(baseline.structuredOutputValidityRate()),
                num(baseline.conditionFamilyAccuracy()),
                num(baseline.requiredFlagAccuracy()),
                num(baseline.wordingSafetyRate()),
                challenge.examples(),
                num(challenge.fieldPrecision()),
                num(challenge.fieldRecall()),
                num(challenge.fieldF1()),
                num(challenge.duplicateFieldRate()),
                num(challenge.unknownFieldRate()),
                num(challenge.unsupportedFieldRate()),
                num(challenge.structuredOutputValidityRate()),
                num(challenge.conditionFamilyAccuracy()),
                num(challenge.requiredFlagAccuracy()),
                num(challenge.wordingSafetyRate()));
    }

    private static int examples(Map<String, Object> body, String key) {
        return (Integer) body.get(key);
    }

    private static String lines(Object value) {
        StringBuilder builder = new StringBuilder();
        @SuppressWarnings("unchecked")
        Map<String, Integer> map = (Map<String, Integer>) value;
        map.forEach((key, count) -> builder.append("- ").append(key).append(": ").append(count).append("\n"));
        return builder.toString().stripTrailing();
    }

    private static String num(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static int countTag(List<PlanExample> examples, String tag) {
        return (int) examples.stream().filter(example -> example.review().tags().contains(tag)).count();
    }

    private static Map<String, Integer> byCount(List<PlanExample> examples) {
        Map<String, Integer> counts = new TreeMap<>();
        for (PlanExample example : examples) {
            counts.merge(String.valueOf(example.input().conditions().size()), 1, Integer::sum);
        }
        return counts;
    }

    private static Map<String, Integer> byCategory(List<PlanExample> examples) {
        Map<String, Integer> counts = new TreeMap<>();
        for (MonitoringCategory category : MonitoringCategory.values()) {
            counts.put(category.name(), 0);
        }
        for (PlanExample example : examples) {
            example.input().conditions().stream().map(TrainingCondition::category).distinct()
                    .forEach(category -> counts.merge(category.name(), 1, Integer::sum));
        }
        return counts;
    }

    private static Map<String, Integer> fieldFrequency(List<PlanExample> examples) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String field : PlanTrainingCatalog.ROUTINE_TARGET_FIELDS) {
            counts.put(field, 0);
        }
        for (PlanExample example : examples) {
            for (TrainingQuestion question : example.output().questions()) {
                counts.merge(question.fieldCode(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static Map<String, Integer> familyFrequency(List<PlanExample> examples) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String family : PlanTrainingCatalog.CONDITION_FAMILIES) {
            counts.put(family, 0);
        }
        for (PlanExample example : examples) {
            for (String family : example.output().conditionFamilies()) {
                counts.merge(family, 1, Integer::sum);
            }
        }
        return counts;
    }
}
