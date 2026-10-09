package com.carevoice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderPropertiesTest {
    @Test
    void aNegativeWindowOrNonPositiveScanIntervalIsRejected() {
        ReminderProperties negativeWindow = new ReminderProperties();
        negativeWindow.setWindowMinutes(-1);
        assertThatThrownBy(negativeWindow::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAREVOICE_REMINDER_WINDOW_MINUTES");

        ReminderProperties idleScan = new ReminderProperties();
        idleScan.setScanIntervalMs(0);
        assertThatThrownBy(idleScan::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAREVOICE_REMINDER_SCAN_INTERVAL_MS");
    }
}
