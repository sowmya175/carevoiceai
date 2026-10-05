package com.carevoice.wording;

import com.carevoice.agent.CollectedFacts;
import com.carevoice.agent.PlannedQuestion;
import com.carevoice.config.CareVoiceAiProperties;
import com.carevoice.domain.MonitoringField;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionWordingTest {
    private final QuestionWordingValidator validator = new QuestionWordingValidator();
    private final PlannedQuestion dizzinessOnset = new PlannedQuestion(
            MonitoringField.DIZZINESS_ONSET,
            "When did the dizziness start?");

    @Test
    void disabledAdaptiveWordingReturnsTheDeterministicQuestion() {
        AdaptiveQuestionWordingService wording = wording(null);

        assertThat(wording.generateQuestion(dizzinessOnset, context())).isEqualTo("When did the dizziness start?");
    }

    @Test
    void successfulGeminiWordingIsUsedWhenItIsOneQuestion() {
        GeminiQuestionWordingService gemini = mock(GeminiQuestionWordingService.class);
        when(gemini.generateQuestion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("When did you first start feeling dizzy today?");

        assertThat(wording(gemini).generateQuestion(dizzinessOnset, context()))
                .isEqualTo("When did you first start feeling dizzy today?");
    }

    @Test
    void geminiFailureUsesTheFallbackQuestion() {
        GeminiQuestionWordingService gemini = mock(GeminiQuestionWordingService.class);
        when(gemini.generateQuestion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new QuestionWordingException());

        assertThat(wording(gemini).generateQuestion(dizzinessOnset, context()))
                .isEqualTo("When did the dizziness start?");
    }

    @Test
    void blankLongAndMultiQuestionWordingUsesTheFallback() {
        assertThat(wording(returning("   ")).generateQuestion(dizzinessOnset, context()))
                .isEqualTo("When did the dizziness start?");
        assertThat(wording(returning("A".repeat(250) + "?")).generateQuestion(dizzinessOnset, context()))
                .isEqualTo("When did the dizziness start?");
        assertThat(wording(returning("Did you faint? Did you fall?")).generateQuestion(dizzinessOnset, context()))
                .isEqualTo("When did the dizziness start?");
        assertThat(wording(returning("Did you faint?\nPlease answer.")).generateQuestion(dizzinessOnset, context()))
                .isEqualTo("When did the dizziness start?");
    }

    @Test
    void structuredOutputReadsOnlyTheQuestion() {
        GeminiQuestionWordingService gemini = new GeminiQuestionWordingService(
                mock(Client.class),
                new CareVoiceAiProperties());

        assertThat(gemini.parse("""
                {"question":"When did you first start feeling dizzy today?","reasoning":"hidden"}
                """)).isEqualTo("When did you first start feeling dizzy today?");
        assertThatThrownBy(() -> gemini.parse("{\"question\":null}"))
                .isInstanceOf(QuestionWordingException.class);
        assertThatThrownBy(() -> gemini.parse(" "))
                .isInstanceOf(QuestionWordingException.class);
    }

    @Test
    void wordingContextIncludesTheSelectedFieldAndOmitsRisk() {
        String message = GeminiQuestionWordingService.userMessage(dizzinessOnset, context());

        assertThat(message).contains("DIZZINESS_ONSET");
        assertThat(message).contains("When did the dizziness start?");
        assertThat(message).contains("I feel dizzy today and my pain is about six.");
        assertThat(message).contains("painScore=6");
        assertThat(message).contains("dizziness=true");
        assertThat(message).doesNotContain("YELLOW");
        assertThat(message).doesNotContain("GREEN");
        assertThat(message).doesNotContain("RED");
        assertThat(validator.acceptable("When did you first start feeling dizzy today?")).isTrue();
    }

    private static QuestionContext context() {
        return new QuestionContext(
                "Tell me how you are feeling today in your own words.",
                "I feel dizzy today and my pain is about six.",
                new CollectedFacts(6, true, null, null, null, null, null, null, null));
    }

    private AdaptiveQuestionWordingService wording(GeminiQuestionWordingService gemini) {
        @SuppressWarnings("unchecked")
        ObjectProvider<GeminiQuestionWordingService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gemini);
        return new AdaptiveQuestionWordingService(
                provider,
                new DeterministicQuestionWordingService(),
                validator);
    }

    private GeminiQuestionWordingService returning(String question) {
        GeminiQuestionWordingService gemini = mock(GeminiQuestionWordingService.class);
        when(gemini.generateQuestion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(question);
        return gemini;
    }
}
