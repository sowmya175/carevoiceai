package com.carevoice.exception;

/**
 * The demo plan catalog is missing, so a check-in cannot choose questions safely.
 */
public class MonitoringPlanUnavailableException extends RuntimeException {
    public static final String CLIENT_MESSAGE =
            "A monitoring plan is not available for this check-in yet.";

    public MonitoringPlanUnavailableException() {
        super(CLIENT_MESSAGE);
    }
}
