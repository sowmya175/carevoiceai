package com.carevoice.agent;
import com.carevoice.service.ExtractedClinicalFacts;
import com.carevoice.service.MonitoringSessionContext;
import com.carevoice.service.RuleBasedClinicalExtractionService;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalExtractionEvaluationTest {
    private final RuleBasedClinicalExtractionService extractor = new RuleBasedClinicalExtractionService();

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void extractsBaselineFacts(String name, String message, Expectation expected) {
        ExtractedClinicalFacts facts = extractor.extract(message, MonitoringSessionContext.empty());

        assertThat(facts.painScore()).isEqualTo(expected.painScore());
        assertThat(facts.dizziness()).isEqualTo(expected.dizziness());
        if (expected.onsetContains() == null) {
            assertThat(facts.dizzinessOnset()).isNull();
        } else {
            assertThat(facts.dizzinessOnset()).containsIgnoringCase(expected.onsetContains());
        }
        assertThat(facts.lossOfConsciousness()).isEqualTo(expected.lossOfConsciousness());
        assertThat(facts.medicationTaken()).isEqualTo(expected.medicationTaken());
        assertThat(facts.appetite()).isEqualTo(expected.appetite());
        assertThat(facts.sleepQuality()).isEqualTo(expected.sleepQuality());
        assertThat(facts.shortnessOfBreath()).isEqualTo(expected.shortnessOfBreath());
        assertThat(facts.temperature()).isEqualTo(expected.temperature());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> examples() {
        return Stream.of(
                example("pain about six", "My pain is about six today.", Expectation.pain(6)),
                example("not dizzy", "I don't feel dizzy.", Expectation.dizziness(false)),
                example("dizzy since morning", "I've been dizzy since this morning.",
                        new Expectation(null, true, "this morning", null, null, null, null, null, null)),
                example("did not pass out", "I didn't pass out.", Expectation.lossOfConsciousness(false)),
                example("passed out", "I passed out when I stood up.", Expectation.lossOfConsciousness(true)),
                example("forgot medicine", "I forgot to take my medicine today.", Expectation.medication(false)),
                example("took medication", "I took my medication this morning.", Expectation.medication(true)),
                example("barely ate", "I barely ate anything today.", Expectation.appetite("poor")),
                example("slept well", "I slept really well last night.", Expectation.sleep("good")),
                example("trouble breathing", "I'm having trouble breathing.", Expectation.shortnessOfBreath(true)),
                example("no trouble breathing", "I don't have any trouble breathing.", Expectation.shortnessOfBreath(false)),
                example("temperature", "My temperature is 100.2.", Expectation.temperature(100.2)),
                example("nonspecific discomfort", "I don't feel very good today.", Expectation.none()),
                example("dizzy now after earlier denial", "I wasn't dizzy earlier but I am dizzy now.", Expectation.dizziness(true))
        );
    }

    private static org.junit.jupiter.params.provider.Arguments example(String name, String message, Expectation expected) {
        return org.junit.jupiter.params.provider.Arguments.of(name, message, expected);
    }

    record Expectation(
            Integer painScore,
            Boolean dizziness,
            String onsetContains,
            Boolean lossOfConsciousness,
            Boolean medicationTaken,
            String appetite,
            String sleepQuality,
            Boolean shortnessOfBreath,
            Double temperature
    ) {
        static Expectation none() {
            return new Expectation(null, null, null, null, null, null, null, null, null);
        }

        static Expectation pain(int score) {
            return new Expectation(score, null, null, null, null, null, null, null, null);
        }

        static Expectation dizziness(boolean value) {
            return new Expectation(null, value, null, null, null, null, null, null, null);
        }

        static Expectation lossOfConsciousness(boolean value) {
            return new Expectation(null, null, null, value, null, null, null, null, null);
        }

        static Expectation medication(boolean value) {
            return new Expectation(null, null, null, null, value, null, null, null, null);
        }

        static Expectation appetite(String value) {
            return new Expectation(null, null, null, null, null, value, null, null, null);
        }

        static Expectation sleep(String value) {
            return new Expectation(null, null, null, null, null, null, value, null, null);
        }

        static Expectation shortnessOfBreath(boolean value) {
            return new Expectation(null, null, null, null, null, null, null, value, null);
        }

        static Expectation temperature(double value) {
            return new Expectation(null, null, null, null, null, null, null, null, value);
        }
    }
}
