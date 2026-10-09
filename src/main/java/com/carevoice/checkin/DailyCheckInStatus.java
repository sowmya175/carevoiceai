package com.carevoice.checkin;

/**
 * Whether the patient has a check-in for a patient-local date.
 * NOT_STARTED is derived when no session exists. The other values match {@code SessionStatus}.
 */
public enum DailyCheckInStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    READY_FOR_REVIEW
}
