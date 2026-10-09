package com.carevoice.reminder;

import com.carevoice.domain.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "reminder_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reminder_patient_date_type",
                columnNames = {"patient_id", "reminder_date", "reminder_type"}
        )
)
public class ReminderNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false)
    private ReminderType reminderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderChannel channel;

    @Column(nullable = false, length = 80)
    private String messageKey;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime deliveredAt;

    private OffsetDateTime readAt;

    @Column(length = 80)
    private String failureType;

    protected ReminderNotification() {}

    public static ReminderNotification pending(
            Patient patient,
            LocalDate reminderDate,
            ReminderTemplate template,
            OffsetDateTime now
    ) {
        ReminderNotification notification = new ReminderNotification();
        notification.patient = patient;
        notification.reminderDate = reminderDate;
        notification.reminderType = ReminderType.DAILY_CHECK_IN;
        notification.status = ReminderStatus.PENDING;
        notification.channel = ReminderChannel.IN_APP;
        notification.messageKey = template.name();
        notification.createdAt = now;
        return notification;
    }

    public void markDelivered(OffsetDateTime deliveredAt) {
        this.status = ReminderStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
        this.failureType = null;
    }

    public void markFailed(String failureType) {
        this.status = ReminderStatus.FAILED;
        this.failureType = failureType == null ? "Unknown" : failureType.substring(0, Math.min(80, failureType.length()));
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public LocalDate getReminderDate() { return reminderDate; }
    public ReminderType getReminderType() { return reminderType; }
    public ReminderStatus getStatus() { return status; }
    public ReminderChannel getChannel() { return channel; }
    public String getMessageKey() { return messageKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
    public String getFailureType() { return failureType; }
}
