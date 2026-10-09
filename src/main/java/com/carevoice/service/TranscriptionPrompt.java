package com.carevoice.service;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;

/**
 * A short speech-to-text hint for the current question.
 * It tells the transcriber what kind of answer to expect and nothing about the patient.
 */
public final class TranscriptionPrompt {
    private TranscriptionPrompt() {}

    public static String forSession(MonitoringSession session) {
        String question = session == null ? null : session.getNextQuestion();
        MonitoringField field = session == null ? null : session.getRequestedField();
        return forTurn(question, field);
    }

    public static String forTurn(String question, MonitoringField requestedField) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("This is a patient health check-in.\n");
        String currentQuestion = oneLine(question);
        if (!currentQuestion.isEmpty()) {
            prompt.append("Current question:\n").append(currentQuestion).append('\n');
        }
        prompt.append("Transcribe exactly what the patient says.\n");
        prompt.append("Do not answer the question.\n");
        prompt.append("Do not infer missing words.");
        if (requestedField == MonitoringField.PAIN_SCORE) {
            prompt.append("\nThe response may contain a number from zero to ten.");
        } else if (requestedField == MonitoringField.MEDICATION_TAKEN
                || requestedField == MonitoringField.LOSS_OF_CONSCIOUSNESS) {
            prompt.append("\nThe response may be a short yes or no, or a natural-language response.");
        }
        return prompt.toString();
    }

    private static String oneLine(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String singleLine = question.replace('\r', ' ').replace('\n', ' ').trim();
        if (singleLine.length() <= 300) {
            return singleLine;
        }
        return singleLine.substring(0, 300).trim();
    }
}
