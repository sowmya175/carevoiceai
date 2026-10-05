package com.carevoice.service;

import com.carevoice.domain.MonitoringField;
import com.carevoice.agent.CollectedFacts;

public final class QuestionClarification {
    public static final String PAIN_SCORE =
            "I didn't catch the pain number. Please say just one number from 0 to 10.";

    private QuestionClarification() {}

    public static String question(MonitoringField field) {
        return switch (field) {
            case PAIN_SCORE -> PAIN_SCORE;
            case MEDICATION_TAKEN -> "I didn't catch whether you took your medication today. Did you take it?";
            case APPETITE -> "Would you say your appetite today has been good, normal, reduced, or poor?";
            case SLEEP_QUALITY -> "Would you say your sleep was good, normal, or poor?";
            case LOSS_OF_CONSCIOUSNESS -> "To confirm, did you actually faint or lose consciousness?";
            case DIZZINESS_ONSET -> "I didn't catch when the dizziness started. When did it begin?";
            case TEMPERATURE -> "I didn't catch the temperature reading. What number does your thermometer show?";
        };
    }

    public static boolean isUnknown(MonitoringField field, CollectedFacts facts) {
        return switch (field) {
            case PAIN_SCORE -> facts.painScore() == null;
            case MEDICATION_TAKEN -> facts.medicationTaken() == null;
            case APPETITE -> facts.appetite() == null || facts.appetite().isBlank();
            case SLEEP_QUALITY -> facts.sleepQuality() == null || facts.sleepQuality().isBlank();
            case LOSS_OF_CONSCIOUSNESS -> facts.lossOfConsciousness() == null;
            case DIZZINESS_ONSET -> facts.dizzinessOnset() == null || facts.dizzinessOnset().isBlank();
            case TEMPERATURE -> facts.temperature() == null;
        };
    }
}
