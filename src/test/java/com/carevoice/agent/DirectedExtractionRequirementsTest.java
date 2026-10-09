package com.carevoice.agent;
import com.carevoice.service.ClinicalExtractionValidator;
import com.carevoice.service.CollectedFacts;
import com.carevoice.service.ExtractedClinicalFacts;
import com.carevoice.domain.MonitoringField;
import com.carevoice.service.MonitoringSessionContext;
import com.carevoice.domain.RiskLevel;
import com.carevoice.service.RuleBasedClinicalExtractionService;
import com.carevoice.domain.SessionStatus;

import com.carevoice.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DirectedExtractionRequirementsTest {
    private final RuleBasedClinicalExtractionService extractor = new RuleBasedClinicalExtractionService();
    private final ClinicalExtractionValidator validator = new ClinicalExtractionValidator();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Yes, I passed out.|true", "No.|false", "No, I didn't actually faint.|false",
            "I almost fainted.|", "I felt like I might pass out.|"
    })
    void distinguishesActualLossOfConsciousnessFromNearSyncope(String answer, Boolean expected) {
        assertThat(extractor.extract(answer, context(MonitoringField.LOSS_OF_CONSCIOUSNESS))
                .lossOfConsciousness()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"I almost fainted.", "I felt like I might pass out."})
    void nearSyncopeCannotBecomeActualLocInProviderOutput(String answer) {
        var hallucinated = new ExtractedClinicalFacts(null, null, null, true, null, null, null, null, null);
        assertThat(validator.validate(hallucinated, answer, context(MonitoringField.LOSS_OF_CONSCIOUSNESS))
                .lossOfConsciousness()).isNull();
        assertThat(extractor.extract(answer, MonitoringSessionContext.empty()).lossOfConsciousness()).isNull();
    }

    @Test
    void numericTemperatureUsesOnlyTheRequestedTemperatureField() {
        var temperature = context(MonitoringField.TEMPERATURE);
        var appetite = context(MonitoringField.APPETITE);
        assertThat(extractor.extract("98.6", temperature).temperature()).isEqualTo(98.6);
        assertThat(extractor.extract("98.6", appetite).temperature()).isNull();
        assertThat(extractor.extract("98.6", MonitoringSessionContext.empty()).temperature()).isNull();
        assertThat(extractor.extract("My temperature is 98.6.", appetite).temperature()).isEqualTo(98.6);
        var modelFacts = new ExtractedClinicalFacts(null, null, null, null, null, null, null, null, 98.6);
        assertThat(validator.validate(modelFacts, "98.6", appetite).temperature()).isNull();
        assertThat(validator.validate(ExtractedClinicalFacts.none(), "98.6", temperature).temperature()).isEqualTo(98.6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"29.9", "115.1", "200", "-98.6", "98 or 99", "warm", "Okay."})
    void rejectsInvalidOrAmbiguousTemperature(String answer) {
        assertThat(extractor.extract(answer, context(MonitoringField.TEMPERATURE)).temperature()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"30", "37", "98.6", "115"})
    void preservesExistingTemperatureRangeAndDoesNotConvertUnits(String answer) {
        assertThat(extractor.extract(answer, context(MonitoringField.TEMPERATURE)).temperature())
                .isEqualTo(Double.valueOf(answer));
    }

    @Test
    void providerCannotResolveTheSpecifiedAmbiguousAnswersIntoFacts() {
        var modelFacts = new ExtractedClinicalFacts(null, null, "Okay", null, true, "poor", "poor", null, null);
        assertThat(validator.validate(modelFacts, "Okay.", context(MonitoringField.MEDICATION_TAKEN)))
                .isEqualTo(ExtractedClinicalFacts.none());
        assertThat(validator.validate(modelFacts, "Okay.", context(MonitoringField.DIZZINESS_ONSET)).dizzinessOnset()).isNull();
        assertThat(validator.validate(modelFacts, "Could be better.", context(MonitoringField.APPETITE)).appetite()).isNull();
        assertThat(validator.validate(modelFacts, "Not great.", context(MonitoringField.SLEEP_QUALITY)).sleepQuality()).isNull();
    }

    private static MonitoringSessionContext context(MonitoringField field) {
        return new MonitoringSessionContext(null, null, CollectedFacts.unknown(), SessionStatus.IN_PROGRESS,
                RiskLevel.GREEN, "Adaptive question", field, 1);
    }
}
