package com.carevoice.service;

import com.carevoice.agent.ExtractedClinicalFacts;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringSessionMergerTest {
    private final MonitoringSessionMerger merger = new MonitoringSessionMerger();

    @Test
    void newNonNullValuesUpdateSession() {
        MonitoringSession session = session();

        merger.merge(session, new ExtractedClinicalFacts(6, true, "this morning", false, true, "reduced", "poor", true, 100.2));

        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
        assertThat(session.getDizzinessOnset()).isEqualTo("this morning");
        assertThat(session.getLossOfConsciousness()).isFalse();
        assertThat(session.getMedicationTaken()).isTrue();
        assertThat(session.getAppetite()).isEqualTo("reduced");
        assertThat(session.getAppetiteReduced()).isTrue();
        assertThat(session.getSleepQuality()).isEqualTo("poor");
        assertThat(session.getShortnessOfBreath()).isTrue();
        assertThat(session.getTemperature()).isEqualTo(100.2);
    }

    @Test
    void nullExtractedValuesDoNotErasePreviouslyCollectedValues() {
        MonitoringSession session = session();
        merger.merge(session, new ExtractedClinicalFacts(6, true, "this morning", false, true, "good", "good", false, 98.6));

        merger.merge(session, ExtractedClinicalFacts.none());

        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
        assertThat(session.getDizzinessOnset()).isEqualTo("this morning");
        assertThat(session.getLossOfConsciousness()).isFalse();
        assertThat(session.getMedicationTaken()).isTrue();
        assertThat(session.getAppetite()).isEqualTo("good");
        assertThat(session.getSleepQuality()).isEqualTo("good");
        assertThat(session.getShortnessOfBreath()).isFalse();
        assertThat(session.getTemperature()).isEqualTo(98.6);
    }

    @Test
    void multipleTurnsPreservePriorFacts() {
        MonitoringSession session = session();

        merger.merge(session, new ExtractedClinicalFacts(6, true, null, null, null, null, null, null, null));
        merger.merge(session, new ExtractedClinicalFacts(null, null, "this morning", null, null, null, null, null, null));
        merger.merge(session, new ExtractedClinicalFacts(null, null, null, false, null, null, null, null, null));

        assertThat(session.getPainScore()).isEqualTo(6);
        assertThat(session.getDizziness()).isTrue();
        assertThat(session.getDizzinessOnset()).isEqualTo("this morning");
        assertThat(session.getLossOfConsciousness()).isFalse();
    }

    private MonitoringSession session() {
        return new MonitoringSession(new Patient("Test", "Daily check-in"));
    }
}
