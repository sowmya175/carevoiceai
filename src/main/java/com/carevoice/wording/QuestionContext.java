package com.carevoice.wording;

import com.carevoice.agent.CollectedFacts;

public record QuestionContext(
        String previousQuestion,
        String latestPatientResponse,
        CollectedFacts knownFacts
) {}
