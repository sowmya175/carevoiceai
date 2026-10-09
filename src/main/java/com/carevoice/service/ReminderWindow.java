package com.carevoice.service;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * A reminder is due from the patient's reminder time through that time plus the window.
 * The window does not cross midnight, so a missed morning reminder is not sent in the evening
 * or on the next local date.
 */
public final class ReminderWindow {
    private ReminderWindow() {}

    public static boolean isDue(LocalTime now, LocalTime reminderTime, int windowMinutes) {
        if (now.isBefore(reminderTime)) {
            return false;
        }
        return ChronoUnit.MINUTES.between(reminderTime, now) <= windowMinutes;
    }
}
