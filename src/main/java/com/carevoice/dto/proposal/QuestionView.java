package com.carevoice.dto.proposal;

import java.util.List;

public record QuestionView(
        Long id,
        String fieldCode,
        String displayName,
        String questionText,
        int displayOrder,
        boolean required,
        boolean enabled,
        String rationale,
        List<Long> relevantConditionIds
) {}
