package com.carevoice.agent;

import com.carevoice.domain.MonitoringSession;

/**
 * Monitoring facts accumulated on a session.
 * Null still means unknown; false means the patient denied it.
 */
public record CollectedFacts(
        Integer painScore,
        Boolean dizziness,
        String dizzinessOnset,
        Boolean lossOfConsciousness,
        Boolean medicationTaken,
        String appetite,
        String sleepQuality,
        Boolean shortnessOfBreath,
        Double temperature
) {
    public static CollectedFacts unknown() {
        return new CollectedFacts(null, null, null, null, null, null, null, null, null);
    }

    public static CollectedFacts from(MonitoringSession session) {
        return new CollectedFacts(
                session.getPainScore(),
                session.getDizziness(),
                session.getDizzinessOnset(),
                session.getLossOfConsciousness(),
                session.getMedicationTaken(),
                session.getAppetite(),
                session.getSleepQuality(),
                session.getShortnessOfBreath(),
                session.getTemperature()
        );
    }
}
