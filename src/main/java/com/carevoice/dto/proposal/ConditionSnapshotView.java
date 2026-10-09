package com.carevoice.dto.proposal;

public record ConditionSnapshotView(
        Long sourcePatientConditionId,
        String conditionName,
        String monitoringCategory,
        boolean primaryCondition
) {}
