package com.carevoice.service;
import com.carevoice.domain.ReminderNotification;
import com.carevoice.repository.ReminderNotificationRepository;
import com.carevoice.domain.ReminderStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InAppNotificationService implements NotificationService {
    private final ReminderNotificationRepository notifications;
    private final Clock clock;

    public InAppNotificationService(ReminderNotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deliver(Long notificationId) {
        ReminderNotification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new IllegalStateException("Reminder notification was not found."));
        if (notification.getStatus() == ReminderStatus.DELIVERED) {
            return;
        }
        notification.markDelivered(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        notifications.save(notification);
    }
}
