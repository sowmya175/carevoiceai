package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.plan.DemoMonitoringPlans;
import com.carevoice.plan.PlanField;
import com.carevoice.service.QuestionClarification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class MissingInformationAnalyzer {

    public List<MonitoringField> missingFields(CollectedFacts facts) {
        return missingFields(facts, DemoMonitoringPlans.general().questions());
    }

    /**
     * Conditional dizziness follow-ups come first when dizziness was reported.
     * Routine gaps then follow the session plan. A field that is not on the plan
     * is not required unless that conditional rule applies.
     */
    public List<MonitoringField> missingFields(CollectedFacts facts, List<PlanField> planFields) {
        List<MonitoringField> missing = new ArrayList<>();
        if (Boolean.TRUE.equals(facts.dizziness())) {
            addIfUnknown(missing, MonitoringField.DIZZINESS_ONSET, facts);
            addIfUnknown(missing, MonitoringField.LOSS_OF_CONSCIOUSNESS, facts);
        }
        List<PlanField> ordered = planFields == null ? List.of() : planFields.stream()
                .filter(PlanField::required)
                .sorted(Comparator.comparingInt(PlanField::displayOrder))
                .toList();
        for (PlanField item : ordered) {
            if (missing.contains(item.field())) {
                continue;
            }
            if (isConditionalFollowUp(item.field()) && !Boolean.TRUE.equals(facts.dizziness())) {
                addIfUnknown(missing, item.field(), facts);
                continue;
            }
            if (!isConditionalFollowUp(item.field())) {
                addIfUnknown(missing, item.field(), facts);
            }
        }
        return List.copyOf(missing);
    }

    private static boolean isConditionalFollowUp(MonitoringField field) {
        return field == MonitoringField.DIZZINESS_ONSET || field == MonitoringField.LOSS_OF_CONSCIOUSNESS;
    }

    private static void addIfUnknown(List<MonitoringField> missing, MonitoringField field, CollectedFacts facts) {
        if (QuestionClarification.isUnknown(field, facts)) {
            missing.add(field);
        }
    }
}
