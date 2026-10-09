package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.plan.DemoMonitoringPlans;
import com.carevoice.plan.PlanField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissingInformationAnalyzerTest {
    private final MissingInformationAnalyzer analyzer = new MissingInformationAnalyzer();

    @Test
    void requiresDailyMonitoringFields() {
        List<MonitoringField> missing = analyzer.missingFields(CollectedFacts.unknown());

        assertThat(missing).containsExactly(
                MonitoringField.PAIN_SCORE,
                MonitoringField.MEDICATION_TAKEN,
                MonitoringField.APPETITE,
                MonitoringField.SLEEP_QUALITY
        );
    }

    @Test
    void requiresDizzinessFollowUpsOnlyWhenDizzinessIsTrue() {
        CollectedFacts facts = new CollectedFacts(6, true, null, null, true, "good", "good", null, null);

        assertThat(analyzer.missingFields(facts)).containsExactly(
                MonitoringField.DIZZINESS_ONSET,
                MonitoringField.LOSS_OF_CONSCIOUSNESS
        );
    }

    @Test
    void doesNotRequireDizzinessFollowUpsWhenDizzinessIsFalse() {
        CollectedFacts facts = new CollectedFacts(6, false, null, null, true, "good", "good", null, null);

        assertThat(analyzer.missingFields(facts)).isEmpty();
    }

    @Test
    void doesNotRequireDizzinessFollowUpsWhenDizzinessIsUnknown() {
        CollectedFacts facts = new CollectedFacts(6, null, null, null, true, "normal", "good", null, null);

        assertThat(analyzer.missingFields(facts)).isEmpty();
    }

    @Test
    void postOperativePlanRequiresOnlyItsFieldsIncludingTemperature() {
        assertThat(analyzer.missingFields(CollectedFacts.unknown(), DemoMonitoringPlans.postOperative().questions()))
                .containsExactly(
                        MonitoringField.PAIN_SCORE,
                        MonitoringField.MEDICATION_TAKEN,
                        MonitoringField.TEMPERATURE,
                        MonitoringField.APPETITE,
                        MonitoringField.SLEEP_QUALITY);
    }

    @Test
    void hypertensionPlanDoesNotRequireFieldsThatUsedToBeGlobal() {
        assertThat(analyzer.missingFields(CollectedFacts.unknown(), DemoMonitoringPlans.hypertension().questions()))
                .containsExactly(MonitoringField.MEDICATION_TAKEN, MonitoringField.SLEEP_QUALITY);
    }

    @Test
    void dizzinessFollowUpComesBeforeRoutinePlanFields() {
        CollectedFacts facts = new CollectedFacts(null, true, null, null, null, null, null, null, null);

        assertThat(analyzer.missingFields(facts, DemoMonitoringPlans.postOperative().questions()))
                .startsWith(MonitoringField.DIZZINESS_ONSET, MonitoringField.LOSS_OF_CONSCIOUSNESS);
    }

    @Test
    void anExplicitPlanQuestionCanAskOnsetWhenDizzinessWasDenied() {
        List<PlanField> plan = List.of(new PlanField(
                MonitoringField.DIZZINESS_ONSET,
                "When did the dizziness start?",
                null,
                1,
                true));
        CollectedFacts facts = new CollectedFacts(null, false, null, null, true, "good", "good", null, null);

        assertThat(analyzer.missingFields(facts, plan)).containsExactly(MonitoringField.DIZZINESS_ONSET);
    }
}
