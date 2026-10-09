package com.carevoice.longitudinal;

import com.carevoice.domain.SessionStatus;
import com.carevoice.history.PatientHistoryResponse.SessionSummary;
import com.carevoice.longitudinal.PatientLongitudinalResponse.*;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.repository.PatientRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
public class LongitudinalAnalysisService {
    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 100;
    private static final List<SessionStatus> INCLUDED_STATUSES =
            List.of(SessionStatus.COMPLETED, SessionStatus.READY_FOR_REVIEW);
    private static final Set<String> SLEEP_CATEGORIES = Set.of("good", "normal", "poor");
    private static final Set<String> APPETITE_CATEGORIES = Set.of("good", "normal", "reduced", "poor");

    private final PatientRepository patients;
    private final MonitoringSessionRepository sessions;
    private final MonitoringTurnRepository turns;

    public LongitudinalAnalysisService(PatientRepository patients, MonitoringSessionRepository sessions,
                                       MonitoringTurnRepository turns) {
        this.patients = patients;
        this.sessions = sessions;
        this.turns = turns;
    }

    @Transactional(readOnly = true)
    public PatientLongitudinalResponse summarize(Long patientId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
        if (!patients.existsById(patientId)) {
            throw new IllegalArgumentException("Patient not found: " + patientId);
        }
        // Bound the most recent eligible sessions in SQL, then order that window oldest first.
        List<SessionFactsSnapshot> window = sessions.findRecentFacts(patientId, INCLUDED_STATUSES,
                        PageRequest.of(0, limit)).stream()
                .sorted(Comparator.comparing((SessionFactsSnapshot row) -> row.startedAt().toInstant())
                        .thenComparing(SessionFactsSnapshot::sessionId))
                .toList();

        Map<Long, Long> turnCounts = new HashMap<>();
        if (!window.isEmpty()) {
            for (Object[] row : turns.countByPatientAndSessionIds(patientId,
                    window.stream().map(SessionFactsSnapshot::sessionId).toList())) {
                turnCounts.put((Long) row[0], (Long) row[1]);
            }
        }

        List<Observation<Integer>> pain = knownObservations(window, SessionFactsSnapshot::painScore);
        Integer firstPain = pain.isEmpty() ? null : pain.getFirst().value();
        Integer latestPain = pain.isEmpty() ? null : pain.getLast().value();
        List<Observation<String>> sleep = observations(window,
                row -> category(row.sleepQuality(), SLEEP_CATEGORIES));
        List<Observation<String>> appetite = observations(window,
                row -> category(row.appetite(), APPETITE_CATEGORIES));
        List<Observation<Boolean>> medication = observations(window, SessionFactsSnapshot::medicationTaken);
        OffsetDateTime start = window.isEmpty() ? null : window.getFirst().startedAt();
        OffsetDateTime end = window.isEmpty() ? null : window.getLast().startedAt();

        return new PatientLongitudinalResponse(patientId, start, end, window.size(), end,
                new PainSummary(pain.size(), firstPain, latestPain,
                        pain.isEmpty() ? null : latestPain - firstPain, pain),
                new SleepSummary(count(sleep, "good"), count(sleep, "normal"), count(sleep, "poor"),
                        count(sleep, null), sleep),
                new AppetiteSummary(count(appetite, "good"), count(appetite, "normal"),
                        count(appetite, "reduced"), count(appetite, "poor"), count(appetite, null), appetite),
                new MedicationSummary(count(medication, true), count(medication, false),
                        count(medication, null), medication),
                new Symptoms(symptom(observations(window, SessionFactsSnapshot::dizziness)),
                        symptom(observations(window, SessionFactsSnapshot::shortnessOfBreath)),
                        symptom(observations(window, SessionFactsSnapshot::lossOfConsciousness))),
                window.stream().filter(row -> row.dizzinessOnset() != null && !row.dizzinessOnset().isBlank())
                        .map(row -> new OnsetObservation(row.sessionId(), row.startedAt(), row.dizzinessOnset()))
                        .toList(),
                knownObservations(window, SessionFactsSnapshot::temperature),
                window.stream().map(row -> new SessionSummary(row.sessionId(), row.startedAt(), row.status(),
                        row.riskLevel(), turnCounts.getOrDefault(row.sessionId(), 0L),
                        row.checkInDate(), row.monitoringPlanName(), null)).toList());
    }

    private static String category(String value, Set<String> supported) {
        // Do not reinterpret unsupported legacy text as a known structured category.
        return value != null && supported.contains(value) ? value : null;
    }

    private static <T> List<Observation<T>> observations(List<SessionFactsSnapshot> window,
                                                        Function<SessionFactsSnapshot, T> value) {
        return window.stream().map(row -> new Observation<>(row.sessionId(), row.startedAt(), value.apply(row)))
                .toList();
    }

    private static <T> List<Observation<T>> knownObservations(List<SessionFactsSnapshot> window,
                                                             Function<SessionFactsSnapshot, T> value) {
        return observations(window, value).stream().filter(observation -> observation.value() != null).toList();
    }

    private static <T> long count(List<Observation<T>> observations, T value) {
        return observations.stream().filter(observation -> Objects.equals(observation.value(), value)).count();
    }

    private static SymptomSummary symptom(List<Observation<Boolean>> observations) {
        List<Observation<Boolean>> reported = observations.stream()
                .filter(observation -> Boolean.TRUE.equals(observation.value())).toList();
        return new SymptomSummary(reported.size(), count(observations, false), count(observations, null),
                reported.isEmpty() ? null : reported.getFirst().timestamp(),
                reported.isEmpty() ? null : reported.getLast().timestamp(), observations);
    }
}
