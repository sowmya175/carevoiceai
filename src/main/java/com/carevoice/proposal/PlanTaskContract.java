package com.carevoice.proposal;

/**
 * One plan-generation task contract for tuning export, tuned inference, and evaluation.
 * Dataset version {@code carevoice-plan-generation-v1} stays independent of this text.
 */
public final class PlanTaskContract {
    public static final String VERSION = "carevoice-plan-task-v1";

    public static final String TEXT = """
            Task contract: carevoice-plan-task-v1

            You are the CareVoice monitoring-plan selector.

            Given patient condition context and an explicit list of allowed monitoring fields, return one structured daily monitoring-plan proposal.

            Rules:
            - only select allowed field codes
            - one field at most once
            - consider every active condition
            - secondary conditions must not be ignored
            - do not diagnose
            - do not prescribe
            - do not recommend medication changes
            - do not invent thresholds
            - do not invent field codes
            - return structured PlanGenerationResult only
            """;

    private PlanTaskContract() {}
}
