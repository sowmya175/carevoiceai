package com.carevoice.proposal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiPlanGenerationModelTest {
    @Test
    void structuredJsonBecomesAPlanResultAndProseDoesNot() {
        GeminiPlanGenerationModel model = new GeminiPlanGenerationModel(null, null);
        PlanGenerationModel.PlanGenerationResult result = model.parse("""
                {"conditionFamilies":["Cardiac surgery recovery"],
                 "questions":[{"fieldCode":"PAIN_SCORE","questionText":"How is your pain today?","required":true,
                 "relevantConditionIds":[8],"rationale":"Recovery monitoring."}]}
                """);
        assertThat(result.conditionFamilies()).containsExactly("Cardiac surgery recovery");
        assertThat(result.questions()).singleElement().satisfies(question -> {
            assertThat(question.fieldCode()).isEqualTo("PAIN_SCORE");
            assertThat(question.required()).isTrue();
            assertThat(question.relevantConditionIds()).containsExactly(8L);
        });
        assertThatThrownBy(() -> model.parse("Please monitor pain, medication, and sleep."))
                .isInstanceOf(PlanGenerationFailedException.class);
    }
}
