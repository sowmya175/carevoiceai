package com.carevoice.domain;

/**
 * One structured question on a monitoring plan.
 * The field is the clinical identifier. The template is display text only.
 */
public record PlanField(
        MonitoringField field,
        String questionTemplate,
        String clarificationTemplate,
        int displayOrder,
        boolean required
) {}
