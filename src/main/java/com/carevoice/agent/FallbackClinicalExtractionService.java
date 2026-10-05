package com.carevoice.agent;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "carevoice.ai", name = "enabled", havingValue = "true")
public class FallbackClinicalExtractionService implements ClinicalExtractionService {
    private static final Logger log = LoggerFactory.getLogger(FallbackClinicalExtractionService.class);

    private final GeminiClinicalExtractionService geminiExtraction;
    private final RuleBasedClinicalExtractionService ruleBasedExtraction;

    public FallbackClinicalExtractionService(
            GeminiClinicalExtractionService geminiExtraction,
            RuleBasedClinicalExtractionService ruleBasedExtraction) {
        this.geminiExtraction = geminiExtraction;
        this.ruleBasedExtraction = ruleBasedExtraction;
    }

    @Override
    public ExtractedClinicalFacts extract(String message, MonitoringSessionContext context) {
        try {
            return geminiExtraction.extract(message, context);
        } catch (ApiException | GenAiIOException | GeminiExtractionException ex) {
            log.warn(
                    "Gemini clinical extraction failed; using deterministic fallback sessionId={} errorType={}",
                    context == null ? null : context.sessionId(),
                    ex.getClass().getSimpleName());
            return ruleBasedExtraction.extract(message, context);
        }
    }
}
