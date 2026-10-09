package com.carevoice.voice;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptionPromptTest {

    @Test
    void painPromptMentionsTheQuestionAndANumericRangeOnly() {
        MonitoringSession session = session("On a scale from 0 to 10, how would you rate your pain today?");
        session.setRequestedField(MonitoringField.PAIN_SCORE);

        String prompt = TranscriptionPrompt.forSession(session);

        assertThat(prompt).contains("Current question:\n" + session.getNextQuestion());
        assertThat(prompt).contains("number from zero to ten");
        assertThat(prompt).doesNotContain("fine");
        assertNoPatientContext(prompt);
    }

    @Test
    void medicationPromptAllowsAShortYesOrNoWithoutInferring() {
        MonitoringSession session = session("Have you taken your prescribed medication today?");
        session.setRequestedField(MonitoringField.MEDICATION_TAKEN);

        String prompt = TranscriptionPrompt.forSession(session);

        assertThat(prompt).contains("short yes or no");
        assertThat(prompt).contains("Do not infer missing words.");
        assertThat(prompt).doesNotContain("number from zero to ten");
        assertNoPatientContext(prompt);
    }

    @Test
    void openingQuestionDoesNotAddAFieldHint() {
        String prompt = TranscriptionPrompt.forSession(
                session("Tell me how you are feeling today in your own words."));

        assertThat(prompt).contains("Current question:");
        assertThat(prompt).doesNotContain("number from zero to ten");
        assertThat(prompt).doesNotContain("short yes or no");
        assertNoPatientContext(prompt);
    }

    private static MonitoringSession session(String question) {
        MonitoringSession session = new MonitoringSession(new Patient("Ada Lovelace", "Daily monitoring"));
        session.setNextQuestion(question);
        session.setRiskLevel(RiskLevel.RED);
        session.setEscalationReason("Pain score is at or above the configured review threshold (7/10).");
        session.setLatestTranscript("I felt dizzy yesterday.");
        return session;
    }

    private static void assertNoPatientContext(String prompt) {
        assertThat(prompt).doesNotContain("Ada Lovelace");
        assertThat(prompt).doesNotContain("Daily monitoring");
        assertThat(prompt).doesNotContain("RED");
        assertThat(prompt).doesNotContain("review threshold");
        assertThat(prompt).doesNotContain("dizzy yesterday");
        assertThat(prompt).doesNotContain("clinical note");
    }
}
