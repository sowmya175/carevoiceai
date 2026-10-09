package com.carevoice.proposal;

import com.carevoice.config.CareVoicePlanAiProperties;
import com.carevoice.domain.MonitoringCategory;
import com.carevoice.proposal.PlanGenerationModel.ConditionInput;
import com.carevoice.proposal.PlanGenerationModel.ExistingPlanInput;
import com.carevoice.proposal.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.proposal.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.training.PlanExample;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanModelRouterTest {
    private final PlanGenerationTrace trace = new PlanGenerationTrace();
    private final PlanOutputGate gate = new PlanOutputGate(Set.of("MEDICATION_TAKEN", "PAIN_SCORE"));
    private final PatientPlanGenerationContext context = new PatientPlanGenerationContext(
            null,
            List.of(new ConditionInput(1L, "Hypertension", MonitoringCategory.HYPERTENSION, true)),
            new ConditionInput(1L, "Hypertension", MonitoringCategory.HYPERTENSION, true),
            List.of(),
            new ExistingPlanInput(false));

    @Test
    void disabledPlanAiUsesTheDeterministicGeneratorOnly() {
        AtomicInteger tuned = new AtomicInteger();
        PlanGenerationResult result = router(false, "vertex-tuned", context1 -> medication(), context1 -> {
            tuned.incrementAndGet();
            return medication();
        }).generate(context);
        assertThat(result.questions()).extracting(GeneratedQuestion::fieldCode).containsExactly("MEDICATION_TAKEN");
        assertThat(tuned).hasValue(0);
        assertThat(trace.readAndClear().provider()).isEqualTo("deterministic");
    }

    @Test
    void geminiBaseDoesNotFallBackToDeterministic() {
        AtomicInteger deterministic = new AtomicInteger();
        PlanModelRouter router = router(true, "gemini-base", context1 -> {
            deterministic.incrementAndGet();
            return medication();
        }, context1 -> medication());
        PlanModelRouter missing = new PlanModelRouter(enabled("gemini-base"), context1 -> {
            deterministic.incrementAndGet();
            return medication();
        }, null, null, gate, trace);
        assertThatThrownBy(() -> missing.generate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini-base");
        assertThat(deterministic).hasValue(0);
        assertThat(router.generate(context).questions()).hasSize(1);
        assertThat(trace.readAndClear().model()).isEqualTo(CareVoicePlanAiProperties.RECOMMENDED_BASE_MODEL);
    }

    @Test
    void tunedProviderRejectsUnknownFieldsAndCanUseConfiguredBaseFallback() {
        PlanModelRouter noFallback = router(true, "vertex-tuned",
                context1 -> medication(), context1 -> unknown());
        assertThatThrownBy(() -> noFallback.generate(context)).isInstanceOf(PlanGenerationFailedException.class);

        CareVoicePlanAiProperties properties = enabled("vertex-tuned");
        properties.setFallbackToBase(true);
        properties.setTunedModel("projects/demo/locations/us-central1/models/carevoice-plan");
        PlanModelRouter fallback = new PlanModelRouter(properties, context1 -> {
            throw new AssertionError("deterministic");
        }, context1 -> medication(), context1 -> unknown(), gate, trace);
        assertThat(fallback.generate(context).questions()).extracting(GeneratedQuestion::fieldCode)
                .containsExactly("MEDICATION_TAKEN");
        GenerationMetadata metadata = trace.readAndClear();
        assertThat(metadata.provider()).isEqualTo("gemini-base");
        assertThat(metadata.datasetVersion()).isEqualTo(PlanExample.DATASET_VERSION);
        assertThat(metadata.taskContractVersion()).isEqualTo(PlanTaskContract.VERSION);
    }

    @Test
    void tunedTimeoutFailsSafelyAndDoesNotUseTheDeterministicGenerator() {
        AtomicInteger deterministic = new AtomicInteger();
        PlanModelRouter router = router(true, "vertex-tuned", context1 -> {
            deterministic.incrementAndGet();
            return medication();
        }, context1 -> {
            throw new RuntimeException("timeout");
        });
        assertThatThrownBy(() -> router.generate(context)).isInstanceOf(PlanGenerationFailedException.class);
        assertThat(deterministic).hasValue(0);
    }

    @Test
    void duplicateFieldsStayForTheExistingValidator() {
        PlanGenerationResult duplicated = new PlanGenerationResult(List.of("HYPERTENSION"), List.of(
                new GeneratedQuestion("MEDICATION_TAKEN", "Have you taken your prescribed medications today?", true, List.of(1L), "a"),
                new GeneratedQuestion("MEDICATION_TAKEN", "Have you taken your prescribed medications today?", true, List.of(1L), "b")));
        PlanModelRouter router = router(true, "vertex-tuned", context1 -> medication(), context1 -> duplicated);
        assertThat(router.generate(context).questions()).hasSize(2);
    }

    @Test
    void tunedModelTypeSatisfiesTheEvaluatorContract() {
        VertexTunedPlanGenerationModel tuned = new VertexTunedPlanGenerationModel(null, enabled("vertex-tuned"));
        PlanGenerationModel model = tuned;
        assertThat(model).isInstanceOf(PlanGenerationModel.class);
    }

    private PlanModelRouter router(
            boolean enabled, String provider, PlanGenerationModel deterministic, PlanGenerationModel alternate) {
        CareVoicePlanAiProperties properties = enabled ? enabled(provider) : new CareVoicePlanAiProperties();
        PlanGenerationModel gemini = "gemini-base".equals(provider) ? alternate : null;
        PlanGenerationModel tuned = "vertex-tuned".equals(provider) ? alternate : null;
        if (!enabled) {
            gemini = null;
            tuned = null;
        }
        return new PlanModelRouter(properties, deterministic, gemini, tuned, gate, trace);
    }

    private static CareVoicePlanAiProperties enabled(String provider) {
        CareVoicePlanAiProperties properties = new CareVoicePlanAiProperties();
        properties.setEnabled(true);
        properties.setProvider(provider);
        properties.setBaseModel(CareVoicePlanAiProperties.RECOMMENDED_BASE_MODEL);
        properties.setTunedModel("projects/demo/locations/us-central1/models/carevoice-plan");
        return properties;
    }

    private static PlanGenerationResult medication() {
        return new PlanGenerationResult(List.of("HYPERTENSION"), List.of(
                new GeneratedQuestion("MEDICATION_TAKEN", "Have you taken your prescribed medications today?", true, List.of(1L), "daily")));
    }

    private static PlanGenerationResult unknown() {
        return new PlanGenerationResult(List.of("HYPERTENSION"), List.of(
                new GeneratedQuestion("BLOOD_PRESSURE", "What is your blood pressure?", true, List.of(1L), "daily")));
    }
}
