package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

public record MonitoringSessionContext(
        Long patientId,
        Long sessionId,
        CollectedFacts collectedFacts,
        SessionStatus status,
        RiskLevel riskLevel,
        String previouslyAskedQuestion,
        MonitoringField previouslyRequestedField,
        int questionsAsked
) {
    public static MonitoringSessionContext empty() {
        return new MonitoringSessionContext(
                null,
                null,
                CollectedFacts.unknown(),
                SessionStatus.IN_PROGRESS,
                RiskLevel.GREEN,
                null,
                null,
                0
        );
    }

    public static MonitoringSessionContext from(MonitoringSession session) {
        Long patientId = session.getPatient() == null ? null : session.getPatient().getId();
        return new MonitoringSessionContext(
                patientId,
                session.getId(),
                CollectedFacts.from(session),
                session.getStatus(),
                session.getRiskLevel(),
                session.getNextQuestion(),
                session.getRequestedField(),
                session.getQuestionsAsked()
        );
    }
}
