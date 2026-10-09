package com.carevoice.dto.monitoring;
import com.carevoice.dto.monitoring.PatientHistoryResponse.SessionSummary;

import java.time.OffsetDateTime;
import java.util.List;

/** All counts and first/last values refer only to the selected session window. */
public record PatientLongitudinalResponse(
        Long patientId,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        int sessionCount,
        OffsetDateTime latestCheckInAt,
        PainSummary pain,
        SleepSummary sleep,
        AppetiteSummary appetite,
        MedicationSummary medication,
        Symptoms symptoms,
        List<OnsetObservation> dizzinessOnset,
        List<Observation<Double>> temperature,
        List<SessionSummary> sessions
) {
    /** Timestamp is the session start time, not the exact time a symptom occurred. */
    public record Observation<T>(Long sessionId, OffsetDateTime timestamp, T value) {}

    public record PainSummary(
            int observationCount,
            Integer firstValue,
            Integer latestValue,
            Integer change,
            List<Observation<Integer>> observations
    ) {}

    public record SleepSummary(
            long goodCount, long normalCount, long poorCount, long unknownCount,
            List<Observation<String>> observations
    ) {}

    public record AppetiteSummary(
            long goodCount, long normalCount, long reducedCount, long poorCount, long unknownCount,
            List<Observation<String>> observations
    ) {}

    public record MedicationSummary(
            long takenCount, long missedCount, long unknownCount,
            List<Observation<Boolean>> observations
    ) {}

    public record Symptoms(
            SymptomSummary dizziness,
            SymptomSummary shortnessOfBreath,
            SymptomSummary lossOfConsciousness
    ) {}

    public record SymptomSummary(
            long reportedTrueCount, long reportedFalseCount, long unknownCount,
            OffsetDateTime firstReportedAt,
            OffsetDateTime lastReportedAt,
            List<Observation<Boolean>> observations
    ) {}

    public record OnsetObservation(Long sessionId, OffsetDateTime timestamp, String onset) {}
}
