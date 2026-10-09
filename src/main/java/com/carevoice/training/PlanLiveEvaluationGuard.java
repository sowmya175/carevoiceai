package com.carevoice.training;

/**
 * Live provider evaluation is opt-in. {@code mvn test} does not call Gemini, upload training data, or submit a tuning job.
 */
public final class PlanLiveEvaluationGuard {
    private PlanLiveEvaluationGuard() {}

    public static boolean enabled() {
        return truthy(System.getenv("CAREVOICE_LIVE_PLAN_EVAL_ENABLED"))
                || truthy(System.getProperty("carevoice.live.plan.eval.enabled"));
    }

    public static void main(String[] args) {
        if (!enabled()) {
            System.out.println("Live plan evaluation is disabled. mvn test does not call a model provider.");
            return;
        }
        System.out.println("Live plan evaluation is enabled. This command does not submit a tuning job.");
    }

    private static boolean truthy(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
