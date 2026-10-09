package com.carevoice.dto.monitoring;

public record SessionResponse(
        Long sessionId,
        String status,
        String riskLevel,
        Integer painScore,
        Boolean dizziness,
        String dizzinessOnset,
        Boolean lossOfConsciousness,
        Boolean medicationTaken,
        Boolean appetiteReduced,
        String appetite,
        String sleepQuality,
        Boolean shortnessOfBreath,
        Double temperature,
        String nextQuestion,
        String escalationReason
) {}
