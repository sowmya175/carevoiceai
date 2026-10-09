package com.carevoice.reminder;
import com.carevoice.service.DailyReminderScheduler;
import com.carevoice.domain.PatientReminderPreference;
import com.carevoice.repository.PatientReminderPreferenceRepository;
import com.carevoice.domain.ReminderChannel;
import com.carevoice.service.ReminderEvaluationService;
import com.carevoice.domain.ReminderNotification;
import com.carevoice.repository.ReminderNotificationRepository;
import com.carevoice.domain.ReminderStatus;

import com.carevoice.domain.AccountRole;
import com.carevoice.domain.UserAccount;
import com.carevoice.repository.UserAccountRepository;
import com.carevoice.checkin.MutableClock;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.SessionStatus;
import com.carevoice.repository.MonitoringSessionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(DailyReminderFlowTest.FixedClock.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reminders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class DailyReminderFlowTest {
    private static final String PASSWORD = "correct-horse-battery";
    private static final String START = "Good morning. Your CareVoice daily check-in is ready.";
    private static final String CONTINUE = "Your previous CareVoice check-in is still in progress.";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-08T11:00:00Z"));
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired MutableClock clock;
    @Autowired ReminderEvaluationService reminders;
    @Autowired ReminderNotificationRepository notifications;
    @Autowired PatientReminderPreferenceRepository preferences;
    @Autowired MonitoringSessionRepository sessions;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectProvider<DailyReminderScheduler> scheduler;
    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.setInstant(Instant.parse("2026-10-08T11:00:00Z"));
    }

    @Test
    void aPatientCanReadAndUpdateAReminderPreferenceAndOthersCannot() throws Exception {
        assertThat(scheduler.getIfAvailable()).isNull();
        register("pref01", "Sarah Miller", "America/New_York", "Post-operative recovery");
        MockHttpSession sarah = login("pref01");
        long sarahId = patientId(sarah);
        accounts.saveAndFlush(new UserAccount("prefdoc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("prefdoc", "/api/auth/login/clinician");

        mvc.perform(get("/api/me/reminder-preference")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/reminder-preference").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reminderTime\":\"08:30\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/reminder-preference").session(doctor)).andExpect(status().isForbidden());
        mvc.perform(put("/api/me/reminder-preference").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reminderTime\":\"09:00\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/me/reminder-preference").session(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reminderTime").value("08:00"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));
        preferences.findByPatient_Id(sarahId).ifPresent(preferences::delete);
        preferences.flush();
        mvc.perform(get("/api/me/reminder-preference").session(sarah))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reminderTime").value("08:00"));
        assertThat(preferences.findByPatient_Id(sarahId)).isEmpty();

        mvc.perform(put("/api/me/reminder-preference").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reminderTime\":\"25:99\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/reminder-preference").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reminderTime\":\"08:30\",\"patientId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminderTime").value("08:30"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));
        assertThat(preferences.findByPatient_Id(sarahId).orElseThrow().getReminderTime()).hasToString("08:30");
    }

    @Test
    void theSameInstantIsDueOnlyInTheTimezoneWhoseLocalTimeIsInsideTheWindow() {
        long newYork = patient("zone-ny", "America/New_York");
        long utc = patient("zone-utc", "UTC");
        long losAngeles = patient("zone-la", "America/Los_Angeles");
        clock.setInstant(Instant.parse("2026-10-08T13:00:00Z"));

        reminders.evaluatePatient(newYork);
        reminders.evaluatePatient(utc);
        reminders.evaluatePatient(losAngeles);

        assertThat(remindersFor(newYork)).hasSize(1);
        assertThat(remindersFor(utc)).isEmpty();
        assertThat(remindersFor(losAngeles)).isEmpty();
    }

    @Test
    void springForwardUsesTheWallClockAfterTheGapAndFallBackSendsOnce() {
        long spring = patient("dst-spring", "America/New_York");
        saveTime(spring, "02:30");
        clock.setInstant(Instant.parse("2026-03-08T06:59:00Z"));
        assertThat(ZonedDateTime.ofInstant(clock.instant(), ZoneId.of("America/New_York")).toLocalTime())
                .hasToString("01:59");
        reminders.evaluatePatient(spring);
        assertThat(remindersFor(spring)).isEmpty();

        clock.setInstant(Instant.parse("2026-03-08T07:00:00Z"));
        assertThat(ZonedDateTime.ofInstant(clock.instant(), ZoneId.of("America/New_York")).toLocalTime())
                .hasToString("03:00");
        reminders.evaluatePatient(spring);
        assertThat(remindersFor(spring)).singleElement()
                .extracting(ReminderNotification::getReminderDate)
                .isEqualTo(LocalDate.of(2026, 3, 8));

        long fall = patient("dst-fall", "America/New_York");
        saveTime(fall, "01:00");
        clock.setInstant(Instant.parse("2026-11-01T05:30:00Z"));
        assertThat(ZonedDateTime.ofInstant(clock.instant(), ZoneId.of("America/New_York")).toLocalTime())
                .hasToString("01:30");
        reminders.evaluatePatient(fall);
        clock.setInstant(Instant.parse("2026-11-01T06:30:00Z"));
        assertThat(ZonedDateTime.ofInstant(clock.instant(), ZoneId.of("America/New_York")).toLocalTime())
                .hasToString("01:30");
        reminders.evaluatePatient(fall);
        assertThat(remindersFor(fall)).singleElement()
                .extracting(ReminderNotification::getReminderDate)
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void notStartedSendsAStartReminderAndLaterStatusesDoNot() throws Exception {
        long ready = patient("elig-ready", "America/New_York");
        clock.setInstant(Instant.parse("2026-10-08T12:00:00Z"));
        reminders.evaluatePatient(ready);
        ReminderNotification sent = remindersFor(ready).get(0);
        assertThat(sent.getStatus()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(sent.getChannel()).isEqualTo(ReminderChannel.IN_APP);
        assertThat(sent.getMessageKey()).isEqualTo("DAILY_CHECK_IN_START");
        assertThat(sent.getReminderDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(sessions.findByPatient_IdOrderByCreatedAtDescIdDesc(ready)).isEmpty();
        reminders.evaluatePatient(ready);
        assertThat(remindersFor(ready)).hasSize(1);

        MockHttpSession readySession = login("elig-ready");
        mvc.perform(get("/api/me/notifications").session(readySession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DAILY_CHECK_IN"))
                .andExpect(jsonPath("$[0].message").value(START))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Recovery"))));

        long progress = patient("elig-progress", "America/New_York");
        start(login("elig-progress"));
        reminders.evaluatePatient(progress);
        assertThat(remindersFor(progress)).isEmpty();

        long completed = patient("elig-done", "America/New_York");
        long completedSession = start(login("elig-done"));
        finish(completedSession, SessionStatus.COMPLETED);
        reminders.evaluatePatient(completed);
        assertThat(remindersFor(completed)).isEmpty();

        long review = patient("elig-review", "America/New_York");
        long reviewSession = start(login("elig-review"));
        finish(reviewSession, SessionStatus.READY_FOR_REVIEW);
        reminders.evaluatePatient(review);
        assertThat(remindersFor(review)).isEmpty();
    }

    @Test
    void anUnfinishedPreviousDayUsesTheContinuationTemplate() throws Exception {
        long patientId = patient("prev01", "America/New_York");
        MockHttpSession session = login("prev01");
        clock.setInstant(Instant.parse("2026-10-09T02:00:00Z"));
        start(session);
        clock.setInstant(Instant.parse("2026-10-09T12:00:00Z"));
        reminders.evaluatePatient(patientId);

        assertThat(remindersFor(patientId)).singleElement()
                .extracting(ReminderNotification::getMessageKey)
                .isEqualTo("PREVIOUS_CHECK_IN_CONTINUE");
        assertThat(sessions.findByPatient_IdOrderByCreatedAtDescIdDesc(patientId)).hasSize(1);
        mvc.perform(get("/api/me/notifications").session(session))
                .andExpect(jsonPath("$[0].message").value(CONTINUE));
    }

    @Test
    void aDisabledPreferenceAndTimesOutsideTheWindowDoNotCreateAReminder() {
        long disabled = patient("off01", "America/New_York");
        PatientReminderPreference preference = preferences.findByPatient_Id(disabled).orElseThrow();
        preference.update(false, java.time.LocalTime.of(8, 0), java.time.OffsetDateTime.parse("2026-10-08T11:00:00Z"));
        preferences.saveAndFlush(preference);
        clock.setInstant(Instant.parse("2026-10-08T12:00:00Z"));
        reminders.evaluatePatient(disabled);
        assertThat(remindersFor(disabled)).isEmpty();

        long early = patient("early01", "America/New_York");
        clock.setInstant(Instant.parse("2026-10-08T11:59:00Z"));
        reminders.evaluatePatient(early);
        assertThat(remindersFor(early)).isEmpty();
        clock.setInstant(Instant.parse("2026-10-08T12:02:00Z"));
        reminders.evaluatePatient(early);
        assertThat(remindersFor(early)).hasSize(1);

        long late = patient("late01", "America/New_York");
        clock.setInstant(Instant.parse("2026-10-08T21:00:00Z"));
        reminders.evaluatePatient(late);
        assertThat(remindersFor(late)).isEmpty();
    }

    @Test
    void completingBeforeTheReminderTimeCreatesNoReminder() throws Exception {
        long patientId = patient("earlydone", "America/New_York");
        clock.setInstant(Instant.parse("2026-10-08T11:30:00Z"));
        long sessionId = start(login("earlydone"));
        finish(sessionId, SessionStatus.COMPLETED);
        clock.setInstant(Instant.parse("2026-10-08T12:00:00Z"));
        reminders.evaluatePatient(patientId);
        assertThat(remindersFor(patientId)).isEmpty();
    }

    @Test
    void aTimeChangeAppliesOnlyWhenTodayHasNotAlreadyBeenDelivered() throws Exception {
        long patientId = patient("time01", "America/New_York");
        MockHttpSession session = login("time01");
        clock.setInstant(Instant.parse("2026-10-08T11:00:00Z"));
        savePreference(session, true, "09:00");
        clock.setInstant(Instant.parse("2026-10-08T12:30:00Z"));
        reminders.evaluatePatient(patientId);
        assertThat(remindersFor(patientId)).isEmpty();

        clock.setInstant(Instant.parse("2026-10-08T13:00:00Z"));
        reminders.evaluatePatient(patientId);
        assertThat(remindersFor(patientId)).hasSize(1);

        savePreference(session, true, "10:00");
        clock.setInstant(Instant.parse("2026-10-08T14:00:00Z"));
        reminders.evaluatePatient(patientId);
        assertThat(remindersFor(patientId)).hasSize(1);
    }

    @Test
    void parallelEvaluationCreatesOneReminder() throws Exception {
        long patientId = patient("race-reminder", "America/New_York");
        clock.setInstant(Instant.parse("2026-10-08T12:00:00Z"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> evaluateTogether(patientId, ready, go));
            Future<?> second = pool.submit(() -> evaluateTogether(patientId, ready, go));
            ready.await();
            go.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(remindersFor(patientId)).hasSize(1);
        assertThat(sessions.findByPatient_IdOrderByCreatedAtDescIdDesc(patientId)).isEmpty();
    }

    @Test
    void aPatientSeesOnlyTheirOwnReminderAndReadingItDoesNotChangeTheCheckIn() throws Exception {
        long sarahId = patient("note-sarah", "America/New_York");
        long johnId = patient("note-john", "America/Chicago");
        clock.setInstant(Instant.parse("2026-10-08T12:00:00Z"));
        reminders.evaluatePatient(sarahId);
        reminders.evaluatePatient(johnId);
        MockHttpSession sarah = login("note-sarah");
        MockHttpSession john = login("note-john");
        long sarahNotification = notificationId(sarah);

        mvc.perform(get("/api/me/notifications").session(john))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + sarahNotification + ")]").isEmpty());
        mvc.perform(post("/api/me/notifications/" + sarahNotification + "/read").session(john).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/me/notifications/" + sarahNotification + "/read").session(sarah).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications").session(sarah))
                .andExpect(jsonPath("$[0].read").value(true))
                .andExpect(jsonPath("$[0].message").value(START));
        mvc.perform(get("/api/me/check-in/today").session(sarah))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
        assertThat(notifications.findById(sarahNotification).orElseThrow().getStatus()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(notifications.findById(sarahNotification).orElseThrow().getReadAt()).isNotNull();
        reminders.evaluatePatient(sarahId);
        assertThat(remindersFor(sarahId)).hasSize(1);
    }

    private void evaluateTogether(long patientId, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        reminders.evaluatePatient(patientId);
    }

    private List<ReminderNotification> remindersFor(long patientId) {
        return notifications.findAll().stream()
                .filter(notification -> notification.getPatient().getId().equals(patientId))
                .toList();
    }

    private void saveTime(long patientId, String time) {
        PatientReminderPreference preference = preferences.findByPatient_Id(patientId).orElseThrow();
        preference.update(true, java.time.LocalTime.parse(time), java.time.OffsetDateTime.parse("2026-10-08T00:00:00Z"));
        preferences.saveAndFlush(preference);
    }

    private void finish(long sessionId, SessionStatus status) {
        MonitoringSession session = sessions.findById(sessionId).orElseThrow();
        session.setStatus(status);
        sessions.saveAndFlush(session);
    }

    private long notificationId(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/me/notifications").session(session)).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$[0].id")).longValue();
    }

    private void savePreference(MockHttpSession session, boolean enabled, String time) throws Exception {
        mvc.perform(put("/api/me/reminder-preference").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + ",\"reminderTime\":\"" + time + "\"}"))
                .andExpect(status().isOk());
    }

    private long patient(String username, String timezone) {
        try {
            register(username, username, timezone, "Recovery");
            return patientId(login(username));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private long start(MockHttpSession session) throws Exception {
        MvcResult started = mvc.perform(post("/api/me/check-in/today").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(started.getResponse().getContentAsString(), "$.sessionId")).longValue();
    }

    private long patientId(MockHttpSession session) throws Exception {
        MvcResult me = mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.patient.id")).longValue();
    }

    private MockHttpSession login(String username) throws Exception {
        return login(username, "/api/auth/login/patient");
    }

    private MockHttpSession login(String username, String path) throws Exception {
        MvcResult result = mvc.perform(post(path).with(csrf())
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
