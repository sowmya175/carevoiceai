package com.carevoice.reminder;

/**
 * Delivers one already-claimed reminder. Implementations must not decide eligibility or create sessions.
 */
public interface NotificationService {
    void deliver(Long notificationId);
}
