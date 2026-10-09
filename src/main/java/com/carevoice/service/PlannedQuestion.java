package com.carevoice.service;

import com.carevoice.domain.MonitoringField;

public record PlannedQuestion(MonitoringField field, String question) {}
