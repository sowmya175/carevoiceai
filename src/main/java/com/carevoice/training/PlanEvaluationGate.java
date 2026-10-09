package com.carevoice.training;
import com.carevoice.training.PlanGenerationEvaluator.EvaluationReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Development promotion checks for a tuned plan model.
 * A pass is not a clinical validation.
 */
public final class PlanEvaluationGate {
    public static final String PASS = "Passed CareVoice development evaluation gates.";
    public static final String FAIL = "Failed CareVoice development evaluation gates.";

    private PlanEvaluationGate() {}

    public static PromotionDecision decide(EvaluationReport test, EvaluationReport challenge) {
        List<String> failures = new ArrayList<>();
        check("test", test, failures);
        check("challenge", challenge, failures);
        if (failures.isEmpty()) {
            return new PromotionDecision(true, List.of(), PASS);
        }
        return new PromotionDecision(false, List.copyOf(failures), FAIL);
    }

    private static void check(String split, EvaluationReport report, List<String> failures) {
        if (report == null) {
            failures.add(split + ": missing evaluation");
            return;
        }
        if (report.unknownFieldRate() > 0) {
            failures.add(split + ": unknown field rate is above zero");
        }
        if (report.unsupportedFieldRate() > 0) {
            failures.add(split + ": unsupported field rate is above zero");
        }
        if (report.structuredOutputValidityRate() < 1.0) {
            failures.add(split + ": structured output validity is below 1");
        }
        if (report.duplicateFieldRate() > 0) {
            failures.add(split + ": duplicate field rate is above zero");
        }
    }

    public record PromotionDecision(boolean passed, List<String> failures, String summary) {}
}
