package com.carevoice.domain;

/**
 * SUPPORTED means the current check-in can extract, store, and display the field.
 * A field can be placed on a monitoring plan only when it is also plan-askable:
 * active, SUPPORTED, and mapped to a {@link MonitoringField} the daily questionnaire asks.
 * CATALOG_ONLY fields are vocabulary for later and must not be activated.
 */
public enum MonitoringFieldRuntimeSupport {
    SUPPORTED,
    CATALOG_ONLY
}
