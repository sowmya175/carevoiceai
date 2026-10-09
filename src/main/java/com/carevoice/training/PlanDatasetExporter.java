package com.carevoice.training;

import com.carevoice.proposal.DeterministicPlanGenerationModel;
import com.carevoice.training.PlanDatasetSplitter.Splits;
import com.carevoice.training.PlanGenerationEvaluator.EvaluationReport;
import com.carevoice.training.PlanTrainingCorpus.Dataset;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlanDatasetExporter {
    private PlanDatasetExporter() {}

    public static ObjectMapper mapper() {
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static void write(Path root) throws IOException {
        Dataset dataset = PlanTrainingCorpus.build();
        ObjectMapper mapper = mapper();
        Files.createDirectories(root.resolve("examples"));
        Files.createDirectories(root.resolve("splits"));
        Files.createDirectories(root.resolve("future_examples"));
        Files.createDirectories(root.resolve("evaluation"));
        write(root.resolve("examples/canonical.json"), mapper.writeValueAsString(dataset.canonical()));
        write(root.resolve("examples/challenge.json"), mapper.writeValueAsString(dataset.challenge()));
        write(root.resolve("splits/train.json"), mapper.writeValueAsString(dataset.splits().train()));
        write(root.resolve("splits/validation.json"), mapper.writeValueAsString(dataset.splits().validation()));
        write(root.resolve("splits/test.json"), mapper.writeValueAsString(dataset.splits().test()));
        write(root.resolve("splits/challenge.json"), mapper.writeValueAsString(ids(dataset.challenge())));
        write(root.resolve("future_examples/catalog-expansion.json"), mapper.writeValueAsString(futureHold()));
        write(root.resolve("manifest.json"), mapper.writeValueAsString(manifest(dataset)));
        EvaluationReport baseline = baseline(dataset);
        EvaluationReport challengeBaseline = PlanGenerationEvaluator.evaluate(
                new DeterministicPlanGenerationModel()::generate, dataset.challenge());
        write(root.resolve("evaluation/deterministic-baseline.json"), mapper.writeValueAsString(baselineView(baseline, challengeBaseline)));
        PlanDatasetReport.write(
                root.resolve("evaluation/dataset-review.md"),
                root.resolve("evaluation/dataset-review.json"),
                dataset, baseline, challengeBaseline, mapper);
    }

    public static String canonicalJson() throws IOException {
        return mapper().writeValueAsString(PlanTrainingCorpus.build().canonical());
    }

    static EvaluationReport baseline(Dataset dataset) {
        List<PlanExample> test = dataset.canonical().stream()
                .filter(example -> dataset.splits().test().contains(example.id()))
                .toList();
        return PlanGenerationEvaluator.evaluate(new DeterministicPlanGenerationModel()::generate, test);
    }

    private static Map<String, Object> manifest(Dataset dataset) {
        Splits splits = dataset.splits();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetVersion", PlanExample.DATASET_VERSION);
        body.put("schemaVersion", PlanExample.SCHEMA_VERSION);
        body.put("createdAt", "2026-10-09");
        body.put("synthetic", true);
        body.put("medicallyValidatedProtocol", false);
        body.put("supportedFieldCodes", PlanTrainingCatalog.planAskableCodes());
        body.put("routineTargetFieldCodes", PlanTrainingCatalog.ROUTINE_TARGET_FIELDS);
        body.put("followUpFieldsExcludedFromTargets", PlanTrainingCatalog.FOLLOW_UP_FIELDS);
        body.put("conditionFamilyVocabulary", PlanTrainingCatalog.CONDITION_FAMILIES);
        body.put("splitStrategy", "Group paraphrases together. Place larger groups first into the split with the most remaining room. Ties prefer train, then validation, then test.");
        body.put("requiredPolicy", "Every routine target question uses required=true.");
        body.put("orderingPolicy", "PAIN_SCORE, MEDICATION_TAKEN, APPETITE, SLEEP_QUALITY, TEMPERATURE");
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("canonical", dataset.canonical().size());
        counts.put("train", splits.train().size());
        counts.put("validation", splits.validation().size());
        counts.put("test", splits.test().size());
        counts.put("challenge", dataset.challenge().size());
        body.put("splitCounts", counts);
        return body;
    }

    private static Map<String, Object> futureHold() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetVersion", PlanExample.DATASET_VERSION);
        body.put("executable", false);
        body.put("includedInSplits", false);
        body.put("reason", "These fields are not routine plan-askable outputs in carevoice-plan-generation-v1.");
        body.put("fields", PlanTrainingCatalog.NOT_PLAN_ASKABLE);
        return body;
    }

    private static Map<String, Object> baselineView(EvaluationReport test, EvaluationReport challenge) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "DeterministicPlanGenerationModel");
        body.putAll(metrics("test", test));
        body.put("challenge", metrics("challenge", challenge));
        return body;
    }

    private static Map<String, Object> metrics(String split, EvaluationReport report) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("split", split);
        body.put("examples", report.examples());
        body.put("fieldPrecision", report.fieldPrecision());
        body.put("fieldRecall", report.fieldRecall());
        body.put("fieldF1", report.fieldF1());
        body.put("duplicateFieldRate", report.duplicateFieldRate());
        body.put("unknownFieldRate", report.unknownFieldRate());
        body.put("unsupportedFieldRate", report.unsupportedFieldRate());
        body.put("structuredOutputValidityRate", report.structuredOutputValidityRate());
        body.put("conditionFamilyAccuracy", report.conditionFamilyAccuracy());
        body.put("requiredFlagAccuracy", report.requiredFlagAccuracy());
        body.put("wordingSafetyRate", report.wordingSafetyRate());
        return body;
    }

    private static List<String> ids(List<PlanExample> examples) {
        return examples.stream().map(PlanExample::id).toList();
    }

    private static void write(Path path, String json) throws IOException {
        Files.writeString(path, json + "\n", StandardCharsets.UTF_8);
    }
}
