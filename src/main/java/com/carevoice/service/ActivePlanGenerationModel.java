package com.carevoice.service;
import com.carevoice.service.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.integration.gemini.GeminiPlanGenerationModel;
import com.carevoice.integration.vertex.VertexTunedPlanGenerationModel;

import com.carevoice.config.CareVoicePlanAiProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The only plan generator used by proposal persistence.
 * Disabled plan AI uses the deterministic generator.
 * An enabled provider does not silently fall back to that generator.
 */
@Component
public class ActivePlanGenerationModel implements PlanGenerationModel {
    private final PlanModelRouter router;

    public ActivePlanGenerationModel(
            CareVoicePlanAiProperties properties,
            DeterministicPlanGenerationModel deterministic,
            ObjectProvider<GeminiPlanGenerationModel> gemini,
            ObjectProvider<VertexTunedPlanGenerationModel> tuned,
            PlanOutputGate gate,
            PlanGenerationTrace trace) {
        this.router = new PlanModelRouter(
                properties, deterministic, gemini.getIfAvailable(), tuned.getIfAvailable(), gate, trace);
    }

    @Override
    public PlanGenerationResult generate(PatientPlanGenerationContext context) {
        return router.generate(context);
    }
}
