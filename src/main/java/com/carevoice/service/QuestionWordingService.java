package com.carevoice.service;

public interface QuestionWordingService {
    String generateQuestion(PlannedQuestion plannedQuestion, QuestionContext context);
}
