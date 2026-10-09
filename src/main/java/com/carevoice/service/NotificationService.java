package com.carevoice.service;

/**
 * Delivers one already-claimed reminder. Implementations must not decide eligibility or create sessions.
 */
public interface NotificationService {
    void deliver(Long notificationId);
}
