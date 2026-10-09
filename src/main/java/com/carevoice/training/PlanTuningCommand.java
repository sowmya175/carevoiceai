package com.carevoice.training;
import com.carevoice.training.PlanTrainingCorpus.Dataset;
import com.carevoice.training.PlanGenerationEvaluator.EvaluationReport;
import com.carevoice.training.PlanEvaluationGate.PromotionDecision;
import com.carevoice.training.PlanDatasetSplitter.Splits;
import com.carevoice.integration.vertex.TuningJobGateway;
import com.carevoice.integration.vertex.VertexTuningJobGateway;

import com.carevoice.config.CareVoicePlanAiProperties;
import com.carevoice.service.DeterministicPlanGenerationModel;
import com.carevoice.service.PlanGenerationModel;
import com.carevoice.service.PlanTaskContract;
import com.carevoice.integration.vertex.VertexTunedPlanGenerationModel;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import com.carevoice.integration.vertex.TuningJobGateway.SubmittedJob;
import com.carevoice.integration.vertex.TuningJobGateway.TuningSubmission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Developer commands for plan-model tuning. The default path validates and exports only.
 * It does not upload data or submit a job unless submit is requested twice: {@code --submit}
 * and {@code CAREVOICE_PLAN_TUNING_SUBMIT=true}.
 */
public final class PlanTuningCommand {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private PlanTuningCommand() {}

    public static void main(String[] args) {
        int code = execute(args, System.getenv(), Path.of("training/plan-generation"), null);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int execute(String[] args, Map<String, String> env, Path root, TuningJobGateway gateway) {
        String command = args == null || args.length == 0 ? "export" : args[0];
        try {
            return switch (command) {
                case "validate" -> validate();
                case "export" -> writeExport(root, env, false, gateway);
                case "submit" -> writeExport(root, env, has(args, "--submit"), gateway);
                case "status" -> status(root, env, has(args, "--refresh"), gateway);
                case "evaluate" -> evaluate(root, env, null);
                default -> usage();
            };
        } catch (IOException ex) {
            System.out.println("Plan tuning command could not write its local files.");
            return 1;
        }
    }

    static int validate() {
        Dataset dataset = PlanTrainingCorpus.build();
        List<String> problems = new ArrayList<>();
        problems.addAll(PlanDatasetValidator.problems(PlanDatasetExporter.mapper(), dataset.canonical()));
        problems.addAll(PlanDatasetValidator.problems(PlanDatasetExporter.mapper(), dataset.challenge()));
        if (!problems.isEmpty()) {
            problems.forEach(System.out::println);
            return 1;
        }
        System.out.println("Dataset validation passed for " + dataset.canonical().size()
                + " canonical examples and " + dataset.challenge().size() + " challenge examples.");
        return 0;
    }

    private static int writeExport(Path root, Map<String, String> env, boolean submitRequested, TuningJobGateway gateway)
            throws IOException {
        if (validate() != 0) {
            return 1;
        }
        Dataset dataset = PlanTrainingCorpus.build();
        VertexPlanTuningExporter.Export export = VertexPlanTuningExporter.export(dataset);
        List<String> phi = new ArrayList<>();
        phi.addAll(VertexPlanTuningExporter.phiFindings(export.trainingJsonl()));
        phi.addAll(VertexPlanTuningExporter.phiFindings(export.validationJsonl()));
        if (!phi.isEmpty()) {
            phi.forEach(System.out::println);
            return 1;
        }
        Path exportDir = root.resolve("provider-export");
        Files.createDirectories(exportDir);
        Files.writeString(exportDir.resolve("train.jsonl"), export.trainingJsonl(), StandardCharsets.UTF_8);
        Files.writeString(exportDir.resolve("validation.jsonl"), export.validationJsonl(), StandardCharsets.UTF_8);
        String baseModel = value(env, "CAREVOICE_PLAN_BASE_MODEL", CareVoicePlanAiProperties.RECOMMENDED_BASE_MODEL);
        String project = value(env, "CAREVOICE_PLAN_PROJECT_ID", "");
        String location = value(env, "CAREVOICE_PLAN_LOCATION", "");
        boolean confirmed = submitRequested && truthy(env.get("CAREVOICE_PLAN_TUNING_SUBMIT"));
        Map<String, Object> report = report(dataset, export, baseModel, project, location, confirmed);
        if (!confirmed) {
            Files.createDirectories(root.resolve("runs/dry-run"));
            Files.writeString(root.resolve("runs/dry-run/export-report.json"),
                    MAPPER.writeValueAsString(report) + "\n", StandardCharsets.UTF_8);
            System.out.println("Dry run only. Training examples: " + export.trainingIds().size()
                    + ". Validation examples: " + export.validationIds().size()
                    + ". Base model: " + baseModel
                    + ". Project: " + blank(project)
                    + ". Location: " + blank(location)
                    + ". Test and challenge splits were not exported.");
            System.out.println("No fine-tuning job was submitted.");
            return 0;
        }
        String trainingUri = env.getOrDefault("CAREVOICE_PLAN_TRAINING_GCS_URI", "");
        String validationUri = env.getOrDefault("CAREVOICE_PLAN_VALIDATION_GCS_URI", "");
        if (trainingUri.isBlank() || validationUri.isBlank() || project.isBlank() || location.isBlank()) {
            System.out.println("Submission needs CAREVOICE_PLAN_PROJECT_ID, CAREVOICE_PLAN_LOCATION, "
                    + "CAREVOICE_PLAN_TRAINING_GCS_URI, and CAREVOICE_PLAN_VALIDATION_GCS_URI. "
                    + "No fine-tuning job was submitted.");
            return 1;
        }
        if (CareVoicePlanAiProperties.NOT_TUNABLE_RUNTIME_MODEL.equalsIgnoreCase(baseModel)) {
            System.out.println("CAREVOICE_PLAN_BASE_MODEL cannot be gemini-3.8-flash. No fine-tuning job was submitted.");
            return 1;
        }
        TuningJobGateway jobs = gateway == null
                ? new VertexTuningJobGateway(project, location, 20_000)
                : gateway;
        String runId = "cv-plan-run-" + RUN_ID.format(Instant.now());
        SubmittedJob job = jobs.submit(new TuningSubmission(
                baseModel, project, location, trainingUri, validationUri, runId));
        report.put("runId", runId);
        report.put("providerJobResource", job.resourceName());
        report.put("status", job.state());
        report.put("submittedAt", Instant.now().toString());
        Path runDir = root.resolve("runs").resolve(runId);
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("run.json"), MAPPER.writeValueAsString(report) + "\n", StandardCharsets.UTF_8);
        System.out.println("Submitted tuning job " + job.resourceName());
        return 0;
    }

    private static int status(Path root, Map<String, String> env, boolean refresh, TuningJobGateway gateway) throws IOException {
        if (!refresh || !truthy(env.get("CAREVOICE_PLAN_TUNING_STATUS"))) {
            System.out.println("Status reads local run files only. No provider status request was made.");
            return 0;
        }
        if (gateway == null) {
            System.out.println("Provider status was not requested with a gateway. No provider status request was made.");
            return 0;
        }
        String resource = env.getOrDefault("CAREVOICE_PLAN_TUNING_JOB", "");
        if (resource.isBlank()) {
            System.out.println("CAREVOICE_PLAN_TUNING_JOB is required to refresh status.");
            return 1;
        }
        System.out.println(gateway.status(resource));
        return 0;
    }

    static int evaluate(Path root, Map<String, String> env, PlanGenerationModel tunedModel) throws IOException {
        Dataset dataset = PlanTrainingCorpus.build();
        DeterministicPlanGenerationModel deterministic = new DeterministicPlanGenerationModel();
        List<PlanExample> testExamples = split(dataset, dataset.splits().test());
        EvaluationReport deterministicTest = PlanGenerationEvaluator.evaluate(deterministic::generate, testExamples);
        EvaluationReport deterministicChallenge = PlanGenerationEvaluator.evaluate(deterministic::generate, dataset.challenge());
        boolean live = truthy(env.get("CAREVOICE_LIVE_PLAN_EVAL_ENABLED"));
        PlanGenerationModel tuned = live ? tunedModel : null;
        if (live && tuned == null) {
            String missing = missingTunedConfig(env);
            if (missing != null) {
                System.out.println(missing + " No fine-tuning job was submitted.");
                return 1;
            }
            tuned = liveTunedModel(env);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetVersion", PlanExample.DATASET_VERSION);
        body.put("taskContractVersion", PlanTaskContract.VERSION);
        body.put("baseModel", value(env, "CAREVOICE_PLAN_BASE_MODEL", CareVoicePlanAiProperties.RECOMMENDED_BASE_MODEL));
        body.put("tunedModel", value(env, "CAREVOICE_PLAN_TUNED_MODEL", ""));
        body.put("trainingExampleCount", dataset.splits().train().size());
        body.put("validationExampleCount", dataset.splits().validation().size());
        body.put("deterministicTest", metrics(deterministicTest));
        body.put("deterministicChallenge", metrics(deterministicChallenge));
        PromotionDecision decision;
        if (tuned == null) {
            decision = new PromotionDecision(false, List.of("Tuned model was not evaluated."), PlanEvaluationGate.FAIL);
            body.put("tunedModelEvaluated", false);
            System.out.println("Deterministic comparison only. " + decision.summary());
        } else {
            EvaluationReport tunedTest = PlanGenerationEvaluator.evaluate(tuned, testExamples);
            EvaluationReport tunedChallenge = PlanGenerationEvaluator.evaluate(tuned, dataset.challenge());
            decision = PlanEvaluationGate.decide(tunedTest, tunedChallenge);
            body.put("tunedModelEvaluated", true);
            body.put("tunedTest", metrics(tunedTest));
            body.put("tunedChallenge", metrics(tunedChallenge));
            System.out.println("Tuned model scored on the test split and the challenge split. " + decision.summary());
        }
        body.put("promotion", decision.summary());
        body.put("promotionPassed", decision.passed());
        body.put("promotionFailures", decision.failures());
        Files.createDirectories(root.resolve("runs/dry-run"));
        Files.writeString(root.resolve("runs/dry-run/evaluation.json"),
                MAPPER.writeValueAsString(body) + "\n", StandardCharsets.UTF_8);
        System.out.println("No fine-tuning job was submitted.");
        return 0;
    }

    private static String missingTunedConfig(Map<String, String> env) {
        String tunedModel = env.getOrDefault("CAREVOICE_PLAN_TUNED_MODEL", "");
        if (tunedModel.isBlank()) {
            return "CAREVOICE_PLAN_TUNED_MODEL is required for live tuned evaluation.";
        }
        if ("latest".equalsIgnoreCase(tunedModel.trim())) {
            return "CAREVOICE_PLAN_TUNED_MODEL must be an immutable resource, not latest.";
        }
        if (env.getOrDefault("CAREVOICE_PLAN_PROJECT_ID", "").isBlank()
                || env.getOrDefault("CAREVOICE_PLAN_LOCATION", "").isBlank()) {
            return "CAREVOICE_PLAN_PROJECT_ID and CAREVOICE_PLAN_LOCATION are required for live tuned evaluation.";
        }
        return null;
    }

    private static PlanGenerationModel liveTunedModel(Map<String, String> env) {
        CareVoicePlanAiProperties properties = new CareVoicePlanAiProperties();
        properties.setTunedModel(env.get("CAREVOICE_PLAN_TUNED_MODEL").trim());
        properties.setTimeoutMillis(20_000);
        Client client = Client.builder()
                .vertexAI(true)
                .project(env.get("CAREVOICE_PLAN_PROJECT_ID").trim())
                .location(env.get("CAREVOICE_PLAN_LOCATION").trim())
                .httpOptions(HttpOptions.builder()
                        .timeout(properties.getTimeoutMillis())
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
        return new VertexTunedPlanGenerationModel(client, properties);
    }

    private static Map<String, Object> report(
            Dataset dataset, VertexPlanTuningExporter.Export export, String baseModel,
            String project, String location, boolean submitted) {
        Splits splits = dataset.splits();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetVersion", PlanExample.DATASET_VERSION);
        body.put("schemaVersion", PlanExample.SCHEMA_VERSION);
        body.put("taskContractVersion", PlanTaskContract.VERSION);
        body.put("baseModel", baseModel);
        body.put("trainingExampleCount", export.trainingIds().size());
        body.put("validationExampleCount", export.validationIds().size());
        body.put("testExampleCount", splits.test().size());
        body.put("challengeExampleCount", dataset.challenge().size());
        body.put("testExported", false);
        body.put("challengeExported", false);
        body.put("project", project);
        body.put("location", location);
        body.put("submitted", submitted);
        return body;
    }

    private static Map<String, Object> metrics(EvaluationReport report) {
        Map<String, Object> body = new LinkedHashMap<>();
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

    private static List<PlanExample> split(Dataset dataset, List<String> ids) {
        return dataset.canonical().stream().filter(example -> ids.contains(example.id())).toList();
    }

    private static int usage() {
        System.out.println("Commands: validate, export, submit, status, evaluate");
        return 2;
    }

    private static boolean has(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean truthy(String value) {
        return "true".equalsIgnoreCase(value);
    }

    private static String value(Map<String, String> env, String key, String fallback) {
        String value = env.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "(not configured)" : value;
    }
}
