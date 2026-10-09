package com.carevoice.service;

import org.springframework.stereotype.Component;

@Component
public class DeterministicQuestionWordingService implements QuestionWordingService {
    @Override
    public String generateQuestion(PlannedQuestion plannedQuestion, QuestionContext context) {
        return plannedQuestion.question();
    }
}
