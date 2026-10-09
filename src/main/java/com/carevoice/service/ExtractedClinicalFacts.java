package com.carevoice.service;

/**
 * Facts mentioned in a single patient turn.
 * A null component means the patient did not address that field.
 * False means the patient explicitly denied it.
 */
public record ExtractedClinicalFacts(
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
    public static ExtractedClinicalFacts none() {
        return new ExtractedClinicalFacts(null, null, null, null, null, null, null, null, null);
    }
}
