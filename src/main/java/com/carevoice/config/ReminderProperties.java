package com.carevoice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carevoice.reminders")
public class ReminderProperties {
    private boolean enabled;
    private long scanIntervalMs = 300_000;
    private int windowMinutes = 240;

    @PostConstruct
    void validate() {
        if (windowMinutes < 0) {
            throw new IllegalStateException("CAREVOICE_REMINDER_WINDOW_MINUTES must be zero or greater.");
        }
        if (scanIntervalMs <= 0) {
            throw new IllegalStateException("CAREVOICE_REMINDER_SCAN_INTERVAL_MS must be positive.");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getScanIntervalMs() { return scanIntervalMs; }
    public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }
    public int getWindowMinutes() { return windowMinutes; }
    public void setWindowMinutes(int windowMinutes) { this.windowMinutes = windowMinutes; }
}
