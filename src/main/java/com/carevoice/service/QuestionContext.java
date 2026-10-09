package com.carevoice.service;

public record QuestionContext(
        String previousQuestion,
        String latestPatientResponse,
        CollectedFacts knownFacts
) {}
