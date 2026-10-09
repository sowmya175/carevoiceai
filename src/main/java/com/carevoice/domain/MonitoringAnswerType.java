package com.carevoice.domain;

/** Catalog answer shape. Not every type is executable in the daily check-in yet. */
public enum MonitoringAnswerType {
    BOOLEAN,
    NUMBER,
    TEXT,
    SINGLE_CHOICE,
    MULTI_CHOICE
}
