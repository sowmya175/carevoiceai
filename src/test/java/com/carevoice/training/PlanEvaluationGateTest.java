package com.carevoice.training;
import com.carevoice.training.PlanGenerationEvaluator.EvaluationReport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanEvaluationGateTest {
    @Test
    void bothSplitsMustStayStructurallySafe() {
        EvaluationReport clean = report(0, 0, 1, 0);
        assertThat(PlanEvaluationGate.decide(clean, clean).summary())
                .isEqualTo("Passed CareVoice development evaluation gates.");
        assertThat(PlanEvaluationGate.decide(clean, clean).summary()).doesNotContain("clinically validated");
        assertThat(PlanEvaluationGate.decide(clean, report(0, 0.1, 1, 0)).passed()).isFalse();
        assertThat(PlanEvaluationGate.decide(report(0.2, 0, 1, 0), clean).passed()).isFalse();
        assertThat(PlanEvaluationGate.decide(report(0, 0, 0.9, 0), clean).passed()).isFalse();
        assertThat(PlanEvaluationGate.decide(report(0, 0, 1, 0.5), clean).passed()).isFalse();
    }

    private static EvaluationReport report(double unknown, double unsupported, double valid, double duplicate) {
        return new EvaluationReport(1, 1, 1, 1, duplicate, unknown, unsupported, valid, 1, 1, 1);
    }
}
