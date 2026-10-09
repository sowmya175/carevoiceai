package com.carevoice.reminder;

import com.carevoice.checkin.MutableClock;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import({ReminderDeliveryFailureTest.FixedClock.class, ReminderDeliveryFailureTest.FailingDelivery.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remindersfail;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class ReminderDeliveryFailureTest {
    private static final String PASSWORD = "correct-horse-battery";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-08T12:00:00Z"));
        }
    }

    @TestConfiguration
    static class FailingDelivery {
        @Bean
        @Primary
        NotificationService failingNotificationService() {
            return new FailingNotificationService();
        }
    }

    static class FailingNotificationService implements NotificationService {
        @Override
        public void deliver(Long notificationId) {
            throw new IllegalStateException("delivery unavailable");
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired MutableClock clock;
    @Autowired ReminderEvaluationService reminders;
    @Autowired ReminderNotificationRepository notifications;
    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.setInstant(Instant.parse("2026-10-08T12:00:00Z"));
    }

    @Test
    void aDeliveryFailureIsRecordedAndCheckInStillStarts() throws Exception {
        register("fail01", "Sarah Miller", "America/New_York", "Post-operative recovery");
        MockHttpSession sarah = login("fail01");
        long sarahId = patientId(sarah);

        reminders.evaluatePatient(sarahId);

        ReminderNotification failed = notifications.findAll().stream()
                .filter(notification -> notification.getPatient().getId().equals(sarahId))
                .findFirst()
                .orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ReminderStatus.FAILED);
        assertThat(failed.getFailureType()).isEqualTo("IllegalStateException");
        assertThat(failed.getMessageKey()).isEqualTo("DAILY_CHECK_IN_START");
        mvc.perform(get("/api/me/notifications").session(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        reminders.evaluatePatient(sarahId);
        assertThat(notifications.findAll().stream().filter(notification -> notification.getPatient().getId().equals(sarahId))).hasSize(1);

        mvc.perform(post("/api/me/check-in/today").session(sarah).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNumber());
        mvc.perform(get("/api/me/check-in/today").session(sarah))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    private long patientId(MockHttpSession session) throws Exception {
        MvcResult me = mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.patient.id")).longValue();
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void register(String username, String name, String timezone, String condition) throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","fullName":"%s","medicalCondition":"%s","timezone":"%s"}
                                """.formatted(username, PASSWORD, name, condition, timezone)))
                .andExpect(status().isCreated());
    }
}
