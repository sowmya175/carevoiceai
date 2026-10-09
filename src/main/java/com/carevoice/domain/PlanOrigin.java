package com.carevoice.domain;

/**
 * Shared demo templates have a null origin on existing rows.
 * An approved suggestion is stored as a patient-specific plan and is not a reusable template.
 */
public enum PlanOrigin {
    DEMO_TEMPLATE,
    PATIENT_SPECIFIC
}
