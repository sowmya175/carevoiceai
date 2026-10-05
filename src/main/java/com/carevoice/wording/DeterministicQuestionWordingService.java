package com.carevoice.wording;

import com.carevoice.agent.PlannedQuestion;
import org.springframework.stereotype.Component;

@Component
public class DeterministicQuestionWordingService implements QuestionWordingService {
    @Override
    public String generateQuestion(PlannedQuestion plannedQuestion, QuestionContext context) {
        return plannedQuestion.question();
    }
}
