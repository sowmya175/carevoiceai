package com.carevoice.domain;

import java.util.List;

public enum MonitoringField {
    DIZZINESS_ONSET,
    LOSS_OF_CONSCIOUSNESS,
    PAIN_SCORE,
    MEDICATION_TAKEN,
    APPETITE,
    SLEEP_QUALITY,
    TEMPERATURE;

    /**
     * Conditional safety follow-ups come first, then routine daily fields.
     * Dizziness onset is asked before loss of consciousness so the agent
     * clarifies the symptom and then screens for fainting.
     */
    public static List<MonitoringField> priorityOrder() {
        return List.of(
                DIZZINESS_ONSET,
                LOSS_OF_CONSCIOUSNESS,
                PAIN_SCORE,
                MEDICATION_TAKEN,
                APPETITE,
                SLEEP_QUALITY,
                TEMPERATURE
        );
    }
}
