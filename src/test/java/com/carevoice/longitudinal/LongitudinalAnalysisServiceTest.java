package com.carevoice.longitudinal;
import com.carevoice.service.LongitudinalAnalysisService;
import com.carevoice.domain.Patient;
import com.carevoice.dto.monitoring.PatientLongitudinalResponse;
import com.carevoice.domain.SessionFactsSnapshot;

import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LongitudinalAnalysisServiceTest {
    private final PatientRepository patients = mock(PatientRepository.class);
    private final MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
    private final MonitoringTurnRepository turns = mock(MonitoringTurnRepository.class);
    private final LongitudinalAnalysisService service = new LongitudinalAnalysisService(patients, sessions, turns);

    @BeforeEach
    void patientExists() {
        when(patients.existsById(7L)).thenReturn(true);
    }

    @Test
    void noHistoryReturnsAnEmptySummary() {
        var response = service.summarize(7L, 30);
        assertThat(response.patientId()).isEqualTo(7L);
        assertThat(response.sessionCount()).isZero();
        assertThat(response.windowStart()).isNull();
        assertThat(response.windowEnd()).isNull();
        assertThat(response.latestCheckInAt()).isNull();
        assertThat(response.pain().observationCount()).isZero();
        assertThat(response.pain().firstValue()).isNull();
        assertThat(response.pain().latestValue()).isNull();
        assertThat(response.pain().change()).isNull();
        assertThat(response.sleep().unknownCount()).isZero();
        assertThat(response.appetite().unknownCount()).isZero();
        assertThat(response.medication().unknownCount()).isZero();
        assertThat(response.symptoms().dizziness().firstReportedAt()).isNull();
        assertThat(response.symptoms().dizziness().lastReportedAt()).isNull();
        assertThat(response.sessions()).isEmpty();
        assertThat(response.temperature()).isEmpty();
        assertThat(response.dizzinessOnset()).isEmpty();
        verifyNoInteractions(turns);
        verify(sessions).findRecentFacts(7L, List.of(SessionStatus.COMPLETED, SessionStatus.READY_FOR_REVIEW),
                PageRequest.of(0, 30));
    }

    @Test
    void oneSessionHasOneObservationPerAvailableField() {
        var row = row(1, 2, "good", "normal", true, true, false, false, "  This morning.  ", 98.6);
        givenRows(row);
        when(turns.countByPatientAndSessionIds(7L, List.of(1L))).thenReturn(List.<Object[]>of(new Object[]{1L, 4L}));

        var response = service.summarize(7L, 30);
        assertThat(response.sessionCount()).isEqualTo(1);
        assertThat(response.windowStart()).isEqualTo(row.startedAt());
        assertThat(response.windowEnd()).isEqualTo(row.startedAt());
        assertThat(response.latestCheckInAt()).isEqualTo(row.startedAt());
        assertThat(response.pain().observationCount()).isEqualTo(1);
        assertThat(response.pain().firstValue()).isEqualTo(2);
        assertThat(response.pain().latestValue()).isEqualTo(2);
        assertThat(response.pain().change()).isZero();
        assertThat(response.sleep().observations()).hasSize(1);
        assertThat(response.appetite().observations()).hasSize(1);
        assertThat(response.medication().observations()).hasSize(1);
        assertThat(response.dizzinessOnset().getFirst().onset()).isEqualTo("  This morning.  ");
        assertThat(response.dizzinessOnset().getFirst().sessionId()).isEqualTo(1L);
        assertThat(response.temperature().getFirst().value()).isEqualTo(98.6);
        assertThat(response.sessions().getFirst().turnCount()).isEqualTo(4);
        assertThat(response.sessions().getFirst().riskLevel()).isEqualTo(RiskLevel.GREEN);
    }

    @Test
    void multipleSessionsMatchTheRequestedDescriptiveExample() {
        givenRows(row(3, 6, "poor", "reduced", false, true, null, null, null, null),
                row(1, 2, "good", "normal", true, false, null, null, null, null),
                row(2, 4, "poor", "reduced", true, false, null, null, null, null));

        var response = service.summarize(7L, 30);
        assertThat(response.pain().observations()).extracting(PatientLongitudinalResponse.Observation::value)
                .containsExactly(2, 4, 6);
        assertThat(response.pain().firstValue()).isEqualTo(2);
        assertThat(response.pain().latestValue()).isEqualTo(6);
        assertThat(response.pain().change()).isEqualTo(4);
        assertThat(response.sleep().goodCount()).isEqualTo(1);
        assertThat(response.sleep().poorCount()).isEqualTo(2);
        assertThat(response.appetite().normalCount()).isEqualTo(1);
        assertThat(response.appetite().reducedCount()).isEqualTo(2);
        assertThat(response.medication().takenCount()).isEqualTo(2);
        assertThat(response.medication().missedCount()).isEqualTo(1);
        var dizziness = response.symptoms().dizziness();
        assertThat(dizziness.reportedTrueCount()).isEqualTo(1);
        assertThat(dizziness.reportedFalseCount()).isEqualTo(2);
        assertThat(dizziness.firstReportedAt()).isEqualTo(date(3));
        assertThat(dizziness.lastReportedAt()).isEqualTo(date(3));
        assertThat(response.sessions()).extracting(summary -> summary.sessionId()).containsExactly(1L, 2L, 3L);
        assertThat(response.sessions()).allSatisfy(summary -> assertThat(summary.turnCount()).isZero());
    }

    @Test
    void unknownIsNotZeroFalseMissedOrNormal() {
        givenRows(row(1, null, null, null, null, null, null, null, null, null),
                row(2, 0, "normal", "normal", false, false, false, false, null, null));
        var response = service.summarize(7L, 30);
        assertThat(response.pain().observations()).extracting(PatientLongitudinalResponse.Observation::value).containsExactly(0);
        assertThat(response.pain().observationCount()).isEqualTo(1);
        assertThat(response.sleep().unknownCount()).isEqualTo(1);
        assertThat(response.sleep().normalCount()).isEqualTo(1);
        assertThat(response.appetite().unknownCount()).isEqualTo(1);
        assertThat(response.appetite().normalCount()).isEqualTo(1);
        assertThat(response.medication().unknownCount()).isEqualTo(1);
        assertThat(response.medication().missedCount()).isEqualTo(1);
        assertThat(response.medication().takenCount()).isZero();
        assertThat(response.medication().observations()).extracting(PatientLongitudinalResponse.Observation::value)
                .containsExactly(null, false);
        for (var symptom : List.of(response.symptoms().dizziness(), response.symptoms().shortnessOfBreath(),
                response.symptoms().lossOfConsciousness())) {
            assertThat(symptom.unknownCount()).isEqualTo(1);
            assertThat(symptom.reportedFalseCount()).isEqualTo(1);
            assertThat(symptom.reportedTrueCount()).isZero();
            assertThat(symptom.firstReportedAt()).isNull();
            assertThat(symptom.lastReportedAt()).isNull();
        }
        assertThat(response.temperature()).isEmpty();
    }

    @Test
    void missingPainDoesNotHideEarlierKnownValuesOrCreateAnObservation() {
        givenRows(row(4, null, null, null, null, null, null, null, null, null),
                row(3, 2, null, null, null, null, null, null, null, null),
                row(2, 6, null, null, null, null, null, null, null, null),
                row(1, null, null, null, null, null, null, null, null, null));
        var response = service.summarize(7L, 30);
        assertThat(response.pain().firstValue()).isEqualTo(6);
        assertThat(response.pain().latestValue()).isEqualTo(2);
        assertThat(response.pain().change()).isEqualTo(-4);
        assertThat(response.pain().observationCount()).isEqualTo(2);
        assertThat(response.latestCheckInAt()).isEqualTo(date(4));
    }

    @Test
    void allCategoriesAreCountedWithoutAssigningSeverity() {
        givenRows(row(1, null, "good", "good", null, null, null, null, null, null),
                row(2, null, "normal", "normal", null, null, null, null, null, null),
                row(3, null, "poor", "reduced", null, null, null, null, null, null),
                row(4, null, null, "poor", null, null, null, null, null, null));
        var response = service.summarize(7L, 30);
        assertThat(response.sleep().goodCount()).isEqualTo(1);
        assertThat(response.sleep().normalCount()).isEqualTo(1);
        assertThat(response.sleep().poorCount()).isEqualTo(1);
        assertThat(response.sleep().unknownCount()).isEqualTo(1);
        assertThat(response.appetite().goodCount()).isEqualTo(1);
        assertThat(response.appetite().normalCount()).isEqualTo(1);
        assertThat(response.appetite().reducedCount()).isEqualTo(1);
        assertThat(response.appetite().poorCount()).isEqualTo(1);
        assertThat(response.appetite().unknownCount()).isZero();
    }

    @Test
    void unsupportedOrBlankCategoriesRemainUnknownAndBlankOnsetsAreOmitted() {
        givenRows(row(1, null, "", " ", null, null, null, null, "  ", null),
                row(2, null, "restless", "excellent", null, null, null, null, null, null));
        var response = service.summarize(7L, 30);
        assertThat(response.sleep().unknownCount()).isEqualTo(2);
        assertThat(response.appetite().unknownCount()).isEqualTo(2);
        assertThat(response.sleep().normalCount()).isZero();
        assertThat(response.appetite().normalCount()).isZero();
        assertThat(response.dizzinessOnset()).isEmpty();
        assertThat(response.pain().change()).isNull();
    }

    @Test
    void symptomDatesUseOnlyExplicitTrueObservationsWithinTheWindow() {
        givenRows(row(5, null, null, null, null, null, null, null, null, null),
                row(4, null, null, null, null, true, true, true, null, null),
                row(3, null, null, null, null, false, false, false, null, null),
                row(2, null, null, null, null, true, true, true, null, null),
                row(1, null, null, null, null, false, false, false, null, null));
        var response = service.summarize(7L, 30);
        for (var symptom : List.of(response.symptoms().dizziness(), response.symptoms().shortnessOfBreath(),
                response.symptoms().lossOfConsciousness())) {
            assertThat(symptom.reportedTrueCount()).isEqualTo(2);
            assertThat(symptom.reportedFalseCount()).isEqualTo(2);
            assertThat(symptom.unknownCount()).isEqualTo(1);
            assertThat(symptom.firstReportedAt()).isEqualTo(date(2));
            assertThat(symptom.lastReportedAt()).isEqualTo(date(4));
            assertThat(symptom.observations()).extracting(PatientLongitudinalResponse.Observation::sessionId)
                    .containsExactly(1L, 2L, 3L, 4L, 5L);
        }
    }

    @Test
    void temperatureValuesAreReturnedUnconvertedAndWithoutClassification() {
        givenRows(row(2, null, null, null, null, null, null, null, null, 98.6),
                row(1, null, null, null, null, null, null, null, null, 37.0));
        assertThat(service.summarize(7L, 30).temperature()).extracting(PatientLongitudinalResponse.Observation::value)
                .containsExactly(37.0, 98.6);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 100})
    void appliesTheRequestedLimitInTheRepositoryQuery(int limit) {
        service.summarize(7L, limit);
        verify(sessions).findRecentFacts(eq(7L), anyList(), eq(PageRequest.of(0, limit)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101, Integer.MAX_VALUE})
    void rejectsInvalidLimitsBeforeLoadingHistory(int limit) {
        assertThatThrownBy(() -> service.summarize(7L, limit)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(sessions, turns);
    }

    @Test
    void missingPatientUsesTheExistingNotFoundConvention() {
        assertThatThrownBy(() -> service.summarize(99L, 30))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Patient not found: 99");
        verifyNoInteractions(sessions, turns);
    }

    private void givenRows(SessionFactsSnapshot... rows) {
        when(sessions.findRecentFacts(eq(7L), anyList(), any())).thenReturn(List.of(rows));
    }

    static OffsetDateTime date(long day) {
        return OffsetDateTime.parse("2026-10-01T09:00:00-04:00").plusDays(day - 1);
    }

    static SessionFactsSnapshot row(long id, Integer pain, String sleep, String appetite, Boolean medication,
                                    Boolean dizziness, Boolean breath, Boolean loc, String onset, Double temperature) {
        return new SessionFactsSnapshot(id, date(id), SessionStatus.COMPLETED, RiskLevel.GREEN,
                pain, sleep, appetite, medication, dizziness, breath, loc, onset, temperature, null, null);
    }
}
