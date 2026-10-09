package com.carevoice.training;
import com.carevoice.training.PlanTrainingCorpus.Dataset;
import com.carevoice.training.VertexPlanTuningExporter.Export;
import com.carevoice.integration.vertex.TuningJobGateway.SubmittedJob;
import com.carevoice.integration.vertex.TuningJobGateway.TuningSubmission;
import com.carevoice.integration.vertex.TuningJobGateway;

import com.carevoice.service.PlanGenerationJson;
import com.carevoice.service.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.service.PlanTaskContract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class VertexPlanTuningExportTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportKeepsTrainAndValidationAndLeavesHeldOutSplitsBehind() throws Exception {
        Dataset dataset = PlanTrainingCorpus.build();
        Export first = VertexPlanTuningExporter.export(dataset);
        Export second = VertexPlanTuningExporter.export(dataset);
        assertThat(first.trainingJsonl()).isEqualTo(second.trainingJsonl());
        assertThat(first.validationJsonl()).isEqualTo(second.validationJsonl());
        assertThat(first.trainingIds()).containsExactlyInAnyOrderElementsOf(dataset.splits().train());
        assertThat(first.validationIds()).containsExactlyInAnyOrderElementsOf(dataset.splits().validation());
        assertThat(new HashSet<>(first.trainingIds())).doesNotContainAnyElementsOf(dataset.splits().test());
        assertThat(new HashSet<>(first.validationIds())).doesNotContainAnyElementsOf(dataset.splits().test());
        assertThat(first.trainingIds()).doesNotContainAnyElementsOf(dataset.challenge().stream().map(PlanExample::id).toList());
        assertThat(VertexPlanTuningExporter.phiFindings(first.trainingJsonl())).isEmpty();
        assertThat(VertexPlanTuningExporter.phiFindings(first.validationJsonl())).isEmpty();

        JsonNode line = mapper.readTree(first.trainingJsonl().lines().findFirst().orElseThrow());
        assertThat(line.fieldNames()).toIterable().containsExactly("systemInstruction", "contents");
        assertThat(line.path("systemInstruction").path("parts").path(0).path("text").asText()).isEqualTo(PlanTaskContract.TEXT);
        assertThat(line.path("contents").path(0).path("role").asText()).isEqualTo("user");
        assertThat(line.path("contents").path(1).path("role").asText()).isEqualTo("model");
        JsonNode user = mapper.readTree(line.path("contents").path(0).path("parts").path(0).path("text").asText());
        assertThat(user.fieldNames()).toIterable().containsExactly("conditions", "allowedFields", "existingPlan");
        assertThat(user.has("patientId")).isFalse();
        assertThat(user.has("patientName")).isFalse();
        assertThat(user.has("username")).isFalse();
        PlanGenerationJson.parse(line.path("contents").path(1).path("parts").path(0).path("text").asText());
    }

    @Test
    void dryRunDoesNotSubmitAndConfirmedSubmitUsesTheGateway() throws Exception {
        Path root = Files.createTempDirectory("carevoice-plan-tuning");
        AtomicBoolean called = new AtomicBoolean();
        TuningJobGateway gateway = new TuningJobGateway() {
            @Override
            public SubmittedJob submit(TuningSubmission submission) {
                called.set(true);
                return new SubmittedJob("projects/demo/locations/us-central1/tuningJobs/1", "JOB_STATE_PENDING");
            }

            @Override
            public String status(String resourceName) {
                called.set(true);
                return "JOB_STATE_SUCCEEDED";
            }
        };
        assertThat(PlanTuningCommand.execute(new String[]{"export"}, Map.of(), root, gateway)).isZero();
        assertThat(called).isFalse();
        assertThat(Files.readString(root.resolve("provider-export/train.jsonl"))).contains("systemInstruction");
        assertThat(Files.readString(root.resolve("runs/dry-run/export-report.json"))).contains("carevoice-plan-task-v1");

        assertThat(PlanTuningCommand.execute(new String[]{"submit", "--submit"}, Map.of(), root, gateway)).isZero();
        assertThat(called).isFalse();

        Map<String, String> confirmed = Map.of(
                "CAREVOICE_PLAN_TUNING_SUBMIT", "true",
                "CAREVOICE_PLAN_PROJECT_ID", "demo-project",
                "CAREVOICE_PLAN_LOCATION", "us-central1",
                "CAREVOICE_PLAN_TRAINING_GCS_URI", "gs://demo/train.jsonl",
                "CAREVOICE_PLAN_VALIDATION_GCS_URI", "gs://demo/validation.jsonl");
        assertThat(PlanTuningCommand.execute(new String[]{"submit", "--submit"}, confirmed, root, gateway)).isZero();
        assertThat(called).isTrue();
        try (var files = Files.walk(root.resolve("runs"))) {
            assertThat(files.anyMatch(path -> "run.json".equals(path.getFileName().toString()))).isTrue();
        }

        called.set(false);
        assertThat(PlanTuningCommand.execute(new String[]{"status"}, Map.of(), root, gateway)).isZero();
        assertThat(called).isFalse();
        assertThat(PlanTuningCommand.execute(new String[]{"evaluate"}, Map.of(), root, gateway)).isZero();
        assertThat(Files.readString(root.resolve("runs/dry-run/evaluation.json")))
                .contains("deterministicTest")
                .contains("deterministicChallenge")
                .contains("Failed CareVoice development evaluation gates.");

        Dataset dataset = PlanTrainingCorpus.build();
        AtomicInteger scored = new AtomicInteger();
        PlanGenerationResult unknown = new PlanGenerationResult(List.of("HYPERTENSION"), List.of(
                new GeneratedQuestion("NOT_A_FIELD", "How is your discomfort today?", true, List.of(1L), "")));
        assertThat(PlanTuningCommand.evaluate(root, Map.of(
                "CAREVOICE_LIVE_PLAN_EVAL_ENABLED", "true",
                "CAREVOICE_PLAN_TUNED_MODEL", "projects/demo/locations/us-central1/models/carevoice-plan"),
                context -> {
                    scored.incrementAndGet();
                    return unknown;
                })).isZero();
        assertThat(scored).hasValue(dataset.splits().test().size() + dataset.challenge().size());
        assertThat(Files.readString(root.resolve("runs/dry-run/evaluation.json")))
                .contains("tunedTest")
                .contains("tunedChallenge")
                .contains("Failed CareVoice development evaluation gates.");
    }
}
