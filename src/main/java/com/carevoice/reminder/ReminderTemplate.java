package com.carevoice.reminder;

/**
 * Neutral reminder copy. These sentences must not include symptoms, conditions, or other health facts.
 */
public enum ReminderTemplate {
    DAILY_CHECK_IN_START("Good morning. Your CareVoice daily check-in is ready."),
    PREVIOUS_CHECK_IN_CONTINUE("Your previous CareVoice check-in is still in progress.");

    private final String message;

    ReminderTemplate(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public static String messageFor(String messageKey) {
        if (messageKey == null) {
            return DAILY_CHECK_IN_START.message;
        }
        try {
            return ReminderTemplate.valueOf(messageKey).message;
        } catch (IllegalArgumentException ex) {
            return DAILY_CHECK_IN_START.message;
        }
    }
}
