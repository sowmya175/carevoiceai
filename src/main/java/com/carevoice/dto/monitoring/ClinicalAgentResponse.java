package com.carevoice.dto.monitoring;
import com.carevoice.service.CollectedFacts;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

import java.util.List;

public record ClinicalAgentResponse(
        Long sessionId,
        String nextQuestion,
        MonitoringField requestedField,
        List<MonitoringField> missingFields,
        RiskLevel riskLevel,
        SessionStatus status,
        boolean conversationComplete,
        CollectedFacts collectedFacts
) {}
