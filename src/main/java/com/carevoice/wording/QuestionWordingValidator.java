package com.carevoice.wording;

import org.springframework.stereotype.Component;

@Component
public class QuestionWordingValidator {
    static final int MAX_LENGTH = 250;

    public boolean acceptable(String question) {
        if (question == null) {
            return false;
        }
        String text = question.trim();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            return false;
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return false;
        }
        return countQuestionMarks(text) == 1;
    }

    private static int countQuestionMarks(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
}
