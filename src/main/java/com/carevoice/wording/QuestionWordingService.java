package com.carevoice.wording;

import com.carevoice.agent.PlannedQuestion;

public interface QuestionWordingService {
    String generateQuestion(PlannedQuestion plannedQuestion, QuestionContext context);
}
