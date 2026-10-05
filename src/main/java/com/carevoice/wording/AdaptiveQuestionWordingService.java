package com.carevoice.wording;

import com.carevoice.agent.PlannedQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AdaptiveQuestionWordingService implements QuestionWordingService {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveQuestionWordingService.class);

    private final ObjectProvider<GeminiQuestionWordingService> geminiWording;
    private final DeterministicQuestionWordingService deterministicWording;
    private final QuestionWordingValidator validator;

    public AdaptiveQuestionWordingService(
            ObjectProvider<GeminiQuestionWordingService> geminiWording,
            DeterministicQuestionWordingService deterministicWording,
            QuestionWordingValidator validator) {
        this.geminiWording = geminiWording;
        this.deterministicWording = deterministicWording;
        this.validator = validator;
    }

    @Override
    public String generateQuestion(PlannedQuestion plannedQuestion, QuestionContext context) {
        String fallback = deterministicWording.generateQuestion(plannedQuestion, context);
        GeminiQuestionWordingService gemini = geminiWording.getIfAvailable();
        if (gemini == null) {
            return fallback;
        }
        try {
            String generated = gemini.generateQuestion(plannedQuestion, context);
            if (validator.acceptable(generated)) {
                return generated.trim();
            }
            log.warn("Adaptive question rejected; using deterministic question field={}", plannedQuestion.field());
            return fallback;
        } catch (RuntimeException ex) {
            log.warn("Adaptive question wording failed; using deterministic question field={} errorType={}",
                    plannedQuestion.field(), ex.getClass().getSimpleName());
            return fallback;
        }
    }
}
