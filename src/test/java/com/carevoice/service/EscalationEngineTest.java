package com.carevoice.service;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationEngineTest {
    private final EscalationEngine engine = new EscalationEngine();

    @Test
    void shortnessOfBreathIsRed() {
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Post-discharge"));
        session.setShortnessOfBreath(true);

        var result = engine.evaluate(session);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.RED);
    }

    @Test
    void painSevenIsYellow() {
        MonitoringSession session = new MonitoringSession(new Patient("Test", "Post-discharge"));
        session.setPainScore(7);

        var result = engine.evaluate(session);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.YELLOW);
    }
}
