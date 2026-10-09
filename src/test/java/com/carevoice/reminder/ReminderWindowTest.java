package com.carevoice.reminder;
import com.carevoice.service.ReminderWindow;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderWindowTest {
    private static final LocalTime EIGHT = LocalTime.of(8, 0);

    @Test
    void aReminderIsDueFromTheConfiguredTimeThroughTheWindow() {
        assertThat(ReminderWindow.isDue(LocalTime.of(7, 59), EIGHT, 240)).isFalse();
        assertThat(ReminderWindow.isDue(LocalTime.of(8, 0), EIGHT, 240)).isTrue();
        assertThat(ReminderWindow.isDue(LocalTime.of(8, 2), EIGHT, 240)).isTrue();
        assertThat(ReminderWindow.isDue(LocalTime.of(12, 0), EIGHT, 240)).isTrue();
        assertThat(ReminderWindow.isDue(LocalTime.of(12, 1), EIGHT, 240)).isFalse();
        assertThat(ReminderWindow.isDue(LocalTime.of(17, 0), EIGHT, 240)).isFalse();
    }
}
