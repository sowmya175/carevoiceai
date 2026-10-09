package com.carevoice.reminder;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReminderInboxService {
    private static final int RECENT_LIMIT = 20;

    private final ReminderNotificationRepository notifications;
    private final java.time.Clock clock;

    public ReminderInboxService(ReminderNotificationRepository notifications, java.time.Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InAppNotificationResponse> list(Long patientId) {
        return notifications.findByPatient_IdAndStatusOrderByCreatedAtDescIdDesc(
                        patientId, ReminderStatus.DELIVERED, PageRequest.of(0, RECENT_LIMIT))
                .stream()
                .map(InAppNotificationResponse::from)
                .toList();
    }

    /**
     * Marks the patient's own notification read. This does not change check-in status or reminder eligibility.
     */
    @Transactional
    public void markRead(Long patientId, Long notificationId) {
        ReminderNotification notification = notifications.findByIdAndPatient_Id(notificationId, patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
        }
    }

    public record InAppNotificationResponse(
            Long id,
            ReminderType type,
            String messageKey,
            String message,
            OffsetDateTime createdAt,
            boolean read
    ) {
        static InAppNotificationResponse from(ReminderNotification notification) {
            return new InAppNotificationResponse(
                    notification.getId(),
                    notification.getReminderType(),
                    notification.getMessageKey(),
                    ReminderTemplate.messageFor(notification.getMessageKey()),
                    notification.getCreatedAt(),
                    notification.getReadAt() != null);
        }
    }
}
