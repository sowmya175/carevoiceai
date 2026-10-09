package com.carevoice.service;
import com.carevoice.service.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.exception.PlanGenerationFailedException;

import com.carevoice.config.CareVoicePlanAiProperties;

import com.carevoice.training.PlanExample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the plan generator from plan-ai configuration.
 * A failure does not switch to the deterministic generator unless plan AI is disabled.
 */
public final class PlanModelRouter implements PlanGenerationModel {
    private static final Logger log = LoggerFactory.getLogger(PlanModelRouter.class);

    private final CareVoicePlanAiProperties properties;
    private final PlanGenerationModel deterministic;
    private final PlanGenerationModel geminiBase;
    private final PlanGenerationModel tuned;
    private final PlanOutputGate gate;
    private final PlanGenerationTrace trace;

    public PlanModelRouter(
            CareVoicePlanAiProperties properties,
            PlanGenerationModel deterministic,
            PlanGenerationModel geminiBase,
            PlanGenerationModel tuned,
            PlanOutputGate gate,
            PlanGenerationTrace trace) {
        this.properties = properties;
        this.deterministic = deterministic;
        this.geminiBase = geminiBase;
        this.tuned = tuned;
        this.gate = gate;
        this.trace = trace;
    }

    @Override
    public PlanGenerationResult generate(PatientPlanGenerationContext context) {
        if (!properties.isEnabled()) {
            return accept("deterministic", "DeterministicPlanGenerationModel", deterministic.generate(context));
        }
        String provider = properties.getProvider() == null ? "" : properties.getProvider().trim();
        if ("gemini-base".equalsIgnoreCase(provider)) {
            if (geminiBase == null) {
                throw new IllegalStateException(
                        "CAREVOICE_PLAN_MODEL_PROVIDER is gemini-base, but the base plan model is not available.");
            }
            return accept("gemini-base", properties.getBaseModel(), geminiBase.generate(context));
        }
        if ("vertex-tuned".equalsIgnoreCase(provider)) {
            if (tuned == null) {
                throw new IllegalStateException(
                        "CAREVOICE_PLAN_MODEL_PROVIDER is vertex-tuned, but the tuned plan model is not available.");
            }
            try {
                return accept("vertex-tuned", properties.getTunedModel(), tuned.generate(context));
            } catch (RuntimeException ex) {
                if (!properties.isFallbackToBase()) {
                    throw failure(ex);
                }
                if (geminiBase == null) {
                    throw new PlanGenerationFailedException();
                }
                log.warn("tuned plan generation failed; trying the base plan model");
                try {
                    return accept("gemini-base", properties.getBaseModel(), geminiBase.generate(context));
                } catch (RuntimeException fallbackFailure) {
                    throw new PlanGenerationFailedException();
                }
            }
        }
        throw new IllegalStateException(
                "CAREVOICE_PLAN_MODEL_PROVIDER must be gemini-base or vertex-tuned when CAREVOICE_PLAN_AI_ENABLED is true.");
    }

    private PlanGenerationResult accept(String provider, String model, PlanGenerationResult result) {
        gate.assertExecutable(result);
        trace.record(new GenerationMetadata(provider, model, PlanExample.DATASET_VERSION, PlanTaskContract.VERSION));
        return result;
    }

    private static RuntimeException failure(RuntimeException ex) {
        if (ex instanceof PlanGenerationFailedException failed) {
            return failed;
        }
        return new PlanGenerationFailedException();
    }
}
