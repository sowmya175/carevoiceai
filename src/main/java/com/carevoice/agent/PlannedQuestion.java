package com.carevoice.agent;

import com.carevoice.domain.MonitoringField;

public record PlannedQuestion(MonitoringField field, String question) {}
