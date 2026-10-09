package com.carevoice.training;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps every paraphrase group inside one split.
 * Groups are ordered by size descending, then group id. Each group goes to the split
 * with the most remaining capacity. Ties prefer train, then validation, then test.
 */
public final class PlanDatasetSplitter {
    private PlanDatasetSplitter() {}

    public static Splits split(List<PlanExample> examples) {
        Map<String, List<PlanExample>> groups = new LinkedHashMap<>();
        for (PlanExample example : examples) {
            groups.computeIfAbsent(example.groupId(), ignored -> new ArrayList<>()).add(example);
        }
        List<List<PlanExample>> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.<List<PlanExample>>comparingInt(List::size).reversed()
                .thenComparing(group -> group.getFirst().groupId()));
        int validationTarget = Math.max(1, (int) Math.round(examples.size() * 0.10));
        int testTarget = Math.max(1, (int) Math.round(examples.size() * 0.10));
        int trainTarget = examples.size() - validationTarget - testTarget;
        List<String> train = new ArrayList<>();
        List<String> validation = new ArrayList<>();
        List<String> test = new ArrayList<>();
        for (List<PlanExample> group : ordered) {
            List<String> destination = pick(train, trainTarget, validation, validationTarget, test, testTarget);
            for (PlanExample example : group) {
                destination.add(example.id());
            }
        }
        return new Splits(List.copyOf(train), List.copyOf(validation), List.copyOf(test));
    }

    private static List<String> pick(
            List<String> train, int trainTarget,
            List<String> validation, int validationTarget,
            List<String> test, int testTarget) {
        int trainRoom = trainTarget - train.size();
        int validationRoom = validationTarget - validation.size();
        int testRoom = testTarget - test.size();
        if (trainRoom >= validationRoom && trainRoom >= testRoom) {
            return train;
        }
        if (validationRoom >= testRoom) {
            return validation;
        }
        return test;
    }

    public record Splits(List<String> train, List<String> validation, List<String> test) {}
}
