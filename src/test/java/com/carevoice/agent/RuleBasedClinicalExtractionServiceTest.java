package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedClinicalExtractionServiceTest {
    private final RuleBasedClinicalExtractionService extractor = new RuleBasedClinicalExtractionService();

    @Test
    void extractsPainScoreFromCommonPhrases() {
        assertThat(extract("My pain is 6.").painScore()).isEqualTo(6);
        assertThat(extract("My pain is six.").painScore()).isEqualTo(6);
        assertThat(extract("The pain is 6 out of 10.").painScore()).isEqualTo(6);
        assertThat(extract("My pain is around 7.").painScore()).isEqualTo(7);
    }

    @Test
    void directedPainAnswersUseTheRequestedField() {
        MonitoringSessionContext pain = painQuestion();

        assertThat(extractor.extract("5", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("five", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("It's five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("My pain is five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("My pain is about 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("I'd say 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("Probably around five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("0 to 10, it is 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("The pain was at 0 to 10 at 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("0 to 10 the pain was at 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("The pain was at 0 to 10 at 5, the pain was at 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("On the scale of 1 to 10 my pain was at 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("On a scale from 1 to 10, I'd say 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("On a scale from zero to ten, five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("On a scale from one to ten, five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("My pain was at 5.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("My pain is about five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("I'd say five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("Probably a five.", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("It's five", pain).painScore()).isEqualTo(5);
        assertThat(extractor.extract("0", pain).painScore()).isEqualTo(0);
        assertThat(extractor.extract("zero", pain).painScore()).isEqualTo(0);
        assertThat(extractor.extract("10", pain).painScore()).isEqualTo(10);
        assertThat(extractor.extract("ten", pain).painScore()).isEqualTo(10);
    }

    @Test
    void directedPainRejectsScoresOutsideZeroToTen() {
        MonitoringSessionContext pain = painQuestion();

        assertThat(extractor.extract("11", pain).painScore()).isNull();
        assertThat(extractor.extract("14", pain).painScore()).isNull();
        assertThat(extractor.extract("The pain is 15.", pain).painScore()).isNull();
        assertThat(extractor.extract("100", pain).painScore()).isNull();
    }

    @Test
    void aNumberIsNotPainUnlessThePainFieldWasRequested() {
        MonitoringSessionContext medication = new MonitoringSessionContext(
                null,
                null,
                CollectedFacts.unknown(),
                SessionStatus.IN_PROGRESS,
                RiskLevel.GREEN,
                "Have you taken your prescribed medication today?",
                MonitoringField.MEDICATION_TAKEN,
                1);

        assertThat(extract("5").painScore()).isNull();
        assertThat(extract("I'd say 5.").painScore()).isNull();
        assertThat(extract("The pain was at 0 to 10 at 5, the pain was at 5.").painScore()).isNull();
        assertThat(extractor.extract("5", medication).painScore()).isNull();
        assertThat(extractor.extract("0 to 10 the pain was at 5.", MonitoringSessionContext.empty()).painScore()).isNull();
        assertThat(extractor.extract("On the scale of 1 to 10 my pain was at 5.", medication).painScore()).isNull();
    }

    @Test
    void doesNotTreatFineAsAPainScore() {
        assertThat(extractor.extract("My pain isn't fine.", painQuestion()).painScore()).isNull();
        assertThat(extractor.extract("I'd say 5 or 6.", painQuestion()).painScore()).isNull();
    }

    @Test
    void extractsDizziness() {
        assertThat(extract("I feel dizzy today.").dizziness()).isTrue();
        assertThat(extract("I've been dizzy.").dizziness()).isTrue();
    }

    @Test
    void handlesNotDizzyAsFalseRatherThanTrue() {
        ExtractedClinicalFacts facts = extract("I am not dizzy.");

        assertThat(facts.dizziness()).isFalse();
    }

    @Test
    void handlesNoDizzinessAsFalse() {
        assertThat(extract("I have no dizziness today.").dizziness()).isFalse();
    }

    @Test
    void extractsPositiveLossOfConsciousness() {
        assertThat(extract("I fainted.").lossOfConsciousness()).isTrue();
        assertThat(extract("I passed out.").lossOfConsciousness()).isTrue();
        assertThat(extract("I lost consciousness.").lossOfConsciousness()).isTrue();
    }

    @Test
    void extractsNegativeLossOfConsciousness() {
        assertThat(extract("I did not faint.").lossOfConsciousness()).isFalse();
        assertThat(extract("I didn't pass out.").lossOfConsciousness()).isFalse();
        assertThat(extract("No, I didn't faint.").lossOfConsciousness()).isFalse();
    }

    @Test
    void extractsMedicationTaken() {
        assertThat(extract("I took my medication.").medicationTaken()).isTrue();
        assertThat(extract("I took my medicine.").medicationTaken()).isTrue();
    }

    @Test
    void extractsMedicationMissed() {
        assertThat(extract("I haven't taken my medication.").medicationTaken()).isFalse();
        assertThat(extract("I forgot my medication.").medicationTaken()).isFalse();
    }

    @Test
    void extractsTemperature() {
        assertThat(extract("My temperature is 100.2.").temperature()).isEqualTo(100.2);
    }

    @Test
    void extractsShortnessOfBreath() {
        assertThat(extract("I am short of breath.").shortnessOfBreath()).isTrue();
        assertThat(extract("I have difficulty breathing.").shortnessOfBreath()).isTrue();
        assertThat(extract("I am having trouble breathing.").shortnessOfBreath()).isTrue();
    }

    @Test
    void leavesUnmentionedFieldsUnknown() {
        ExtractedClinicalFacts facts = extract("My pain is 4.");

        assertThat(facts.painScore()).isEqualTo(4);
        assertThat(facts.dizziness()).isNull();
        assertThat(facts.lossOfConsciousness()).isNull();
        assertThat(facts.medicationTaken()).isNull();
        assertThat(facts.appetite()).isNull();
        assertThat(facts.sleepQuality()).isNull();
        assertThat(facts.shortnessOfBreath()).isNull();
        assertThat(facts.temperature()).isNull();
    }

    @Test
    void directedShortAnswersFollowTheRequestedField() {
        MonitoringSessionContext fainting = new MonitoringSessionContext(
                null,
                null,
                CollectedFacts.unknown(),
                com.carevoice.domain.SessionStatus.IN_PROGRESS,
                com.carevoice.domain.RiskLevel.YELLOW,
                "Did you faint or lose consciousness?",
                MonitoringField.LOSS_OF_CONSCIOUSNESS,
                1);
        MonitoringSessionContext medication = new MonitoringSessionContext(
                null,
                null,
                CollectedFacts.unknown(),
                com.carevoice.domain.SessionStatus.IN_PROGRESS,
                com.carevoice.domain.RiskLevel.GREEN,
                "Have you taken your prescribed medication today?",
                MonitoringField.MEDICATION_TAKEN,
                1);

        assertThat(extractor.extract("Nope.", fainting).lossOfConsciousness()).isFalse();
        assertThat(extractor.extract("Yeah, right after breakfast.", medication).medicationTaken()).isTrue();
        assertThat(extract("Nope.").lossOfConsciousness()).isNull();
    }

    @Test
    void attributesOnsetAnswerToTheQuestionThatWasAsked() {
        MonitoringSessionContext context = new MonitoringSessionContext(
                1L,
                2L,
                CollectedFacts.unknown(),
                com.carevoice.domain.SessionStatus.IN_PROGRESS,
                com.carevoice.domain.RiskLevel.YELLOW,
                "When did the dizziness start?",
                MonitoringField.DIZZINESS_ONSET,
                1
        );

        ExtractedClinicalFacts facts = extractor.extract("It started this morning when I stood up.", context);

        assertThat(facts.dizzinessOnset()).isEqualTo("this morning when I stood up");
        assertThat(facts.painScore()).isNull();
    }

    private ExtractedClinicalFacts extract(String message) {
        return extractor.extract(message, MonitoringSessionContext.empty());
    }

    @Test
    void naturalDirectedAnswersPopulateTheRequestedField() {
        assertThat(extractor.extract("Yes, I did take my medication today.", directed(MonitoringField.MEDICATION_TAKEN)).medicationTaken()).isTrue();
        assertThat(extractor.extract("I did.", directed(MonitoringField.MEDICATION_TAKEN)).medicationTaken()).isTrue();
        assertThat(extractor.extract("Not yet.", directed(MonitoringField.MEDICATION_TAKEN)).medicationTaken()).isFalse();
        assertThat(extractor.extract("I didn't take it.", directed(MonitoringField.MEDICATION_TAKEN)).medicationTaken()).isFalse();
        assertThat(extractor.extract("I keep waking up in the middle of the night.", directed(MonitoringField.SLEEP_QUALITY)).sleepQuality()).isEqualTo("poor");
        assertThat(extractor.extract("I haven’t really felt hungry today.", directed(MonitoringField.APPETITE)).appetite()).isEqualTo("reduced");
        assertThat(extractor.extract("Normal.", directed(MonitoringField.SLEEP_QUALITY)).sleepQuality()).isEqualTo("normal");
    }

    @Test
    void acknowledgmentsAndUncertaintyRemainUnknownForEveryRequestedField() {
        for (MonitoringField field : MonitoringField.values()) {
            for (String answer : new String[]{"Okay.", "OK", "Maybe.", "Not sure.", "I don't know."}) {
                assertThat(extractor.extract(answer, directed(field))).as("%s: %s", field, answer)
                        .isEqualTo(ExtractedClinicalFacts.none());
            }
        }
        assertThat(extractor.extract("Yes, maybe.", directed(MonitoringField.MEDICATION_TAKEN)).medicationTaken()).isNull();
        assertThat(extractor.extract("I will take it later.", directed(MonitoringField.MEDICATION_TAKEN)).medicationTaken()).isNull();
    }

    @Test
    void shortAnswersDoNotLeakIntoOtherFields() {
        assertThat(extractor.extract("Yes.", directed(MonitoringField.MEDICATION_TAKEN)).lossOfConsciousness()).isNull();
        assertThat(extractor.extract("Yes.", directed(MonitoringField.LOSS_OF_CONSCIOUSNESS)).medicationTaken()).isNull();
        assertThat(extract("I did.").medicationTaken()).isNull();
        assertThat(extract("I didn't take it.").medicationTaken()).isNull();
        assertThat(extract("Normal.").sleepQuality()).isNull();
        assertThat(extractor.extract("Good.", directed(MonitoringField.APPETITE)).sleepQuality()).isNull();
    }

    private static MonitoringSessionContext directed(MonitoringField field) {
        return new MonitoringSessionContext(null, null, CollectedFacts.unknown(), SessionStatus.IN_PROGRESS,
                RiskLevel.GREEN, "Adaptive wording differs", field, 1);
    }

    private static MonitoringSessionContext painQuestion() {
        return new MonitoringSessionContext(
                null,
                null,
                CollectedFacts.unknown(),
                SessionStatus.IN_PROGRESS,
                RiskLevel.GREEN,
                "On a scale from 0 to 10, how would you rate your pain today?",
                MonitoringField.PAIN_SCORE,
                1);
    }
}
