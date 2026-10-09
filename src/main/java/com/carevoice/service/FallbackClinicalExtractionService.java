package com.carevoice.service;
import com.carevoice.integration.gemini.GeminiClinicalExtractionService;
import com.carevoice.exception.GeminiExtractionException;

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
    private final ClinicalExtractionRoutingPolicy routingPolicy;

    public FallbackClinicalExtractionService(
            GeminiClinicalExtractionService geminiExtraction,
            RuleBasedClinicalExtractionService ruleBasedExtraction,
            ClinicalExtractionRoutingPolicy routingPolicy) {
        this.geminiExtraction = geminiExtraction;
        this.ruleBasedExtraction = ruleBasedExtraction;
        this.routingPolicy = routingPolicy;
    }

    @Override
    public ExtractedClinicalFacts extract(String message, MonitoringSessionContext context) {
        ExtractedClinicalFacts rules = VoiceTiming.hybridExtraction(
                () -> ruleBasedExtraction.extract(message, context));
        Long sessionId = context == null ? null : context.sessionId();
        if (!routingPolicy.geminiRequired(rules, context)) {
            VoiceTiming.log(log, "geminiExtraction=SKIPPED geminiExtractionMs=0 sessionId=" + sessionId);
            return rules;
        }
        long started = System.nanoTime();
        try {
            ExtractedClinicalFacts geminiFacts = geminiExtraction.extract(message, context);
            VoiceTiming.log(log, "geminiExtraction=CALLED geminiExtractionMs="
                    + VoiceTiming.millisSince(started) + " sessionId=" + sessionId);
            return geminiFacts;
        } catch (ApiException | GenAiIOException | GeminiExtractionException ex) {
            VoiceTiming.log(log, "geminiExtraction=FAILED_FALLBACK geminiExtractionMs="
                    + VoiceTiming.millisSince(started) + " sessionId=" + sessionId
                    + " errorType=" + ex.getClass().getSimpleName());
            log.warn("Gemini clinical extraction failed; using deterministic fallback sessionId={} errorType={}",
                    sessionId, ex.getClass().getSimpleName());
            return rules;
        }
    }
}
