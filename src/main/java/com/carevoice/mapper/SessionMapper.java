package com.carevoice.mapper;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.dto.monitoring.SessionResponse;

public final class SessionMapper {
    private SessionMapper() {}

    public static SessionResponse toResponse(MonitoringSession session) {
        return new SessionResponse(
                session.getId(),
                session.getStatus().name(),
                session.getRiskLevel().name(),
                session.getPainScore(),
                session.getDizziness(),
                session.getDizzinessOnset(),
                session.getLossOfConsciousness(),
                session.getMedicationTaken(),
                session.getAppetiteReduced(),
                session.getAppetite(),
                session.getSleepQuality(),
                session.getShortnessOfBreath(),
                session.getTemperature(),
                session.getNextQuestion(),
                session.getEscalationReason());
    }
}
