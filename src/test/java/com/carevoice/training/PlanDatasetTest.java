package com.carevoice.training;

import com.carevoice.domain.MonitoringCategory;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.training.PlanExample.TrainingOutput;
import com.carevoice.training.PlanExample.TrainingQuestion;
import com.carevoice.training.PlanTrainingCorpus.Dataset;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanDatasetTest {
    private final ObjectMapper mapper = PlanDatasetExporter.mapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @Test
    void canonicalDatasetValidatesAndMatchesTheCheckedInFiles() throws Exception {
        Path root = Path.of("training/plan-generation");
        if ("true".equals(System.getenv("CAREVOICE_EXPORT_DATASET"))) {
            PlanDatasetExporter.write(root);
        }
        Dataset dataset = PlanTrainingCorpus.build();
        assertThat(PlanDatasetValidator.problems(mapper, dataset.canonical())).isEmpty();
        assertThat(PlanDatasetValidator.problems(mapper, dataset.challenge())).isEmpty();
        assertThat(dataset.canonical()).hasSizeBetween(100, 200);
        long multi = dataset.canonical().stream().filter(example -> example.input().conditions().size() > 1).count();
        assertThat((double) multi / dataset.canonical().size()).isBetween(0.35, 0.50);
        assertThat(checked(root.resolve("examples/canonical.json"))).isEqualTo(checkedText(PlanDatasetExporter.canonicalJson()));
        assertSplits(dataset);
        for (PlanExample example : dataset.canonical()) {
            PlanGenerationResult result = mapper.convertValue(example.output(), PlanGenerationResult.class);
            assertThat(result.questions()).hasSize(example.output().questions().size());
            assertThat(example.input().conditions().stream().filter(condition -> condition.primary()).count()).isEqualTo(1);
            assertThat(example.output().conditionFamilies()).allMatch(PlanTrainingCatalog.CONDITION_FAMILIES::contains);
        }
        JsonNode future = mapper.readTree(Files.readString(root.resolve("future_examples/catalog-expansion.json")));
        assertThat(future.get("executable").asBoolean()).isFalse();
        assertThat(future.get("includedInSplits").asBoolean()).isFalse();
    }

    @Test
    void validatorNamesTheExampleWhenATargetUsesAnUnsupportedField() {
        Dataset dataset = PlanTrainingCorpus.build();
        PlanExample source = dataset.canonical().getFirst();
        TrainingQuestion invented = new TrainingQuestion(
                "INCISION_STATUS", "How does the incision look today?", true, List.of(1L), "Included.");
        PlanExample broken = new PlanExample(
                source.id(), source.groupId(), source.datasetVersion(), source.review(), source.input(),
                new TrainingOutput(List.of("OTHER"), List.of(invented)));
        assertThat(PlanDatasetValidator.problems(mapper, broken).toString()).contains(source.id());
    }

    @Test
    void phiScannerFlagsIdentityFieldsAndEmails() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("patientId", "42");
        node.put("conditionName", "nurse@example.com");
        assertThat(PlanPhiScanner.findings("cv-plan-000099", node).toString())
                .contains("patientId")
                .contains("email");
    }

    @Test
    void liveEvaluationStaysOffDuringTheTestSuite() {
        assertThat(PlanLiveEvaluationGuard.enabled()).isFalse();
    }

    private static void assertSplits(Dataset dataset) throws Exception {
        Set<String> train = new HashSet<>(dataset.splits().train());
        Set<String> validation = new HashSet<>(dataset.splits().validation());
        Set<String> test = new HashSet<>(dataset.splits().test());
        assertThat(train).doesNotContainAnyElementsOf(validation);
        assertThat(train).doesNotContainAnyElementsOf(test);
        assertThat(validation).doesNotContainAnyElementsOf(test);
        Set<String> canonicalIds = new HashSet<>();
        for (PlanExample example : dataset.canonical()) {
            canonicalIds.add(example.id());
        }
        Set<String> union = new HashSet<>(train);
        union.addAll(validation);
        union.addAll(test);
        assertThat(union).isEqualTo(canonicalIds);
        for (PlanExample challenge : dataset.challenge()) {
            assertThat(union).doesNotContain(challenge.id());
        }
        java.util.Map<String, String> groupSplit = new java.util.HashMap<>();
        assignGroups(dataset, "train", dataset.splits().train(), groupSplit);
        assignGroups(dataset, "validation", dataset.splits().validation(), groupSplit);
        assignGroups(dataset, "test", dataset.splits().test(), groupSplit);
        int total = dataset.canonical().size();
        assertThat((double) dataset.splits().validation().size() / total).isBetween(0.08, 0.16);
        assertThat((double) dataset.splits().test().size() / total).isBetween(0.08, 0.16);
        for (MonitoringCategory category : MonitoringCategory.values()) {
            assertThat(dataset.canonical().stream()
                    .flatMap(example -> example.input().conditions().stream())
                    .filter(condition -> condition.category() == category)
                    .findAny()).isPresent();
        }
    }

    private static void assignGroups(
            Dataset dataset, String splitName, List<String> ids, java.util.Map<String, String> groupSplit) {
        for (PlanExample example : dataset.canonical()) {
            if (!ids.contains(example.id())) {
                continue;
            }
            String previous = groupSplit.putIfAbsent(example.groupId(), splitName);
            assertThat(previous == null || previous.equals(splitName)).isTrue();
        }
    }

    private static String checked(Path path) throws Exception {
        return checkedText(Files.readString(path));
    }

    private static String checkedText(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
