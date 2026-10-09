package com.carevoice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "carevoice.reminders", name = "enabled", havingValue = "true")
public class DailyReminderScheduler {
    private final ReminderEvaluationService reminders;

    public DailyReminderScheduler(ReminderEvaluationService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${carevoice.reminders.scan-interval-ms:300000}")
    public void scan() {
        reminders.evaluateDueReminders();
    }
}
