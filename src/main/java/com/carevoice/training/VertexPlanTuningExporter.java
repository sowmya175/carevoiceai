package com.carevoice.training;
import com.carevoice.training.PlanTrainingCorpus.Dataset;

import com.carevoice.service.PlanGenerationJson;
import com.carevoice.service.PlanGenerationPrompt;
import com.carevoice.service.PlanTaskContract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the canonical dataset into Vertex supervised-tuning JSONL.
 * The canonical examples stay the source of truth. Test and challenge rows are never exported.
 */
public final class VertexPlanTuningExporter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VertexPlanTuningExporter() {}

    public static Export export(Dataset dataset) {
        List<PlanExample> train = byIds(dataset.canonical(), dataset.splits().train());
        List<PlanExample> validation = byIds(dataset.canonical(), dataset.splits().validation());
        String trainingJsonl = jsonl(train);
        String validationJsonl = jsonl(validation);
        assertHeldOut(trainingJsonl, validationJsonl, dataset);
        return new Export(
                train.stream().map(PlanExample::id).toList(),
                validation.stream().map(PlanExample::id).toList(),
                trainingJsonl,
                validationJsonl);
    }

    public static String jsonl(List<PlanExample> examples) {
        StringBuilder builder = new StringBuilder();
        for (PlanExample example : examples) {
            builder.append(line(example)).append('\n');
        }
        return builder.toString();
    }

    public static String line(PlanExample example) {
        Map<String, Object> systemPart = Map.of("text", PlanTaskContract.TEXT);
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("parts", List.of(systemPart));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("systemInstruction", system);
        line.put("contents", List.of(
                turn("user", PlanGenerationPrompt.userText(example.input())),
                turn("model", PlanGenerationPrompt.outputText(example.output()))));
        try {
            return MAPPER.writeValueAsString(line);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not write a tuning example for " + example.id());
        }
    }

    private static Map<String, Object> turn(String role, String text) {
        Map<String, Object> part = Map.of("text", text);
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("role", role);
        turn.put("parts", List.of(part));
        return turn;
    }

    private static List<PlanExample> byIds(List<PlanExample> examples, List<String> ids) {
        Map<String, PlanExample> byId = new LinkedHashMap<>();
        for (PlanExample example : examples) {
            byId.put(example.id(), example);
        }
        List<PlanExample> selected = new ArrayList<>();
        for (String id : ids) {
            PlanExample example = byId.get(id);
            if (example == null) {
                throw new IllegalStateException("Split id is not in the canonical dataset: " + id);
            }
            selected.add(example);
        }
        return List.copyOf(selected);
    }

    private static void assertHeldOut(String trainingJsonl, String validationJsonl, Dataset dataset) {
        for (String id : dataset.splits().test()) {
            if (trainingJsonl.contains(id) || validationJsonl.contains(id)) {
                throw new IllegalStateException("Test example leaked into a tuning payload: " + id);
            }
        }
        for (PlanExample example : dataset.challenge()) {
            if (trainingJsonl.contains(example.id()) || validationJsonl.contains(example.id())) {
                throw new IllegalStateException("Challenge example leaked into a tuning payload: " + example.id());
            }
        }
    }

    public static List<String> phiFindings(String jsonl) {
        List<String> findings = new ArrayList<>();
        int lineNumber = 1;
        for (String line : jsonl.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = MAPPER.readTree(line);
                findings.addAll(PlanPhiScanner.findings("line-" + lineNumber, node));
                findings.addAll(scanText("line-" + lineNumber + "-user", node.path("contents").path(0).path("parts").path(0).path("text")));
                JsonNode modelText = node.path("contents").path(1).path("parts").path(0).path("text");
                findings.addAll(scanText("line-" + lineNumber + "-model", modelText));
                if (modelText.isTextual()) {
                    PlanGenerationJson.parse(modelText.asText());
                }
            } catch (Exception ex) {
                findings.add("line-" + lineNumber + ": " + ex.getClass().getSimpleName());
            }
            lineNumber++;
        }
        return findings;
    }

    private static List<String> scanText(String id, JsonNode text) {
        if (!text.isTextual()) {
            return List.of(id + ": missing text");
        }
        try {
            return PlanPhiScanner.findings(id, MAPPER.readTree(text.asText()));
        } catch (Exception ex) {
            return List.of(id + ": " + ex.getClass().getSimpleName());
        }
    }

    public record Export(
            List<String> trainingIds,
            List<String> validationIds,
            String trainingJsonl,
            String validationJsonl
    ) {}
}
