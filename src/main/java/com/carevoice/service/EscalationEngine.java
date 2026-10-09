package com.carevoice.service;
import com.carevoice.domain.Patient;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.RiskLevel;
import org.springframework.stereotype.Service;

/**
 * Prototype safety rules for this monitoring MVP.
 * Thresholds such as pain at or above 7 are demonstration defaults, not validated clinical guidelines.
 * Language understanding must not replace this engine.
 */
@Service
public class EscalationEngine {

    public Evaluation evaluate(MonitoringSession session) {
        if (Boolean.TRUE.equals(session.getShortnessOfBreath())) {
            return new Evaluation(RiskLevel.RED, "Patient reported shortness of breath; clinician review required by configured safety rule.");
        }
        if (Boolean.TRUE.equals(session.getLossOfConsciousness())) {
            return new Evaluation(RiskLevel.RED, "Patient reported loss of consciousness; clinician review required by configured safety rule.");
        }
        if (session.getPainScore() != null && session.getPainScore() >= 7) {
            return new Evaluation(RiskLevel.YELLOW, "Pain score is at or above the configured review threshold (7/10).");
        }
        if (Boolean.TRUE.equals(session.getDizziness())) {
            return new Evaluation(RiskLevel.YELLOW, "New dizziness/lightheadedness was reported and should be reviewed.");
        }
        if (Boolean.FALSE.equals(session.getMedicationTaken())) {
            return new Evaluation(RiskLevel.YELLOW, "Patient reported missing prescribed medication.");
        }
        return new Evaluation(RiskLevel.GREEN, null);
    }

    public record Evaluation(RiskLevel riskLevel, String reason) {}
}
