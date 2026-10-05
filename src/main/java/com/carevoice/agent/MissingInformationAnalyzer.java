package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MissingInformationAnalyzer {

    public List<MonitoringField> missingFields(CollectedFacts facts) {
        List<MonitoringField> missing = new ArrayList<>();
        for (MonitoringField field : MonitoringField.priorityOrder()) {
            if (isMissing(field, facts)) {
                missing.add(field);
            }
        }
        return List.copyOf(missing);
    }

    private boolean isMissing(MonitoringField field, CollectedFacts facts) {
        return switch (field) {
            case PAIN_SCORE -> facts.painScore() == null;
            case MEDICATION_TAKEN -> facts.medicationTaken() == null;
            case APPETITE -> isBlank(facts.appetite());
            case SLEEP_QUALITY -> isBlank(facts.sleepQuality());
            // Temperature is optional unless explicitly requested by the current flow.
            case TEMPERATURE -> false;
            case DIZZINESS_ONSET -> Boolean.TRUE.equals(facts.dizziness()) && isBlank(facts.dizzinessOnset());
            case LOSS_OF_CONSCIOUSNESS -> Boolean.TRUE.equals(facts.dizziness()) && facts.lossOfConsciousness() == null;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
