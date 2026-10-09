package com.carevoice.dto.proposal;

import java.time.Instant;
import java.util.List;

public record ProposalView(
        Long id,
        Long patientId,
        String status,
        Instant createdAt,
        Instant approvedAt,
        Instant rejectedAt,
        Long approvedMonitoringPlanId,
        String approvedMonitoringPlanName,
        List<ConditionSnapshotView> conditions,
        List<String> conditionFamilies,
        List<String> warnings,
        List<QuestionView> questions
) {}
