package com.carevoice.checkin;

import com.carevoice.auth.AccountRole;
import com.carevoice.auth.UserAccount;
import com.carevoice.auth.UserAccountRepository;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.SessionStatus;
import com.carevoice.plan.DemoMonitoringPlans;
import com.carevoice.repository.MonitoringPlanRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.PatientRepository;
import com.carevoice.repository.SessionPlanQuestionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(DailyCheckInFlowTest.FixedClock.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:daily;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class DailyCheckInFlowTest {
    private static final String PASSWORD = "correct-horse-battery";

    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-08T15:00:00Z"));
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired MutableClock clock;
    @Autowired DailyCheckInService dailyCheckIns;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired PatientRepository patients;
    @Autowired MonitoringSessionRepository sessions;
    @Autowired SessionPlanQuestionRepository sessionQuestions;
    @Autowired MonitoringPlanRepository plans;
    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.setInstant(Instant.parse("2026-10-08T15:00:00Z"));
    }

    @Test
    void oneSessionPerPatientLocalDateAndTheNextDateUsesTheCurrentPlan() throws Exception {
        register("sarah01", "Sarah Miller", "America/New_York", "Post-operative recovery");
        MockHttpSession sarah = login("sarah01");
        long sarahId = patientId(sarah);

        mvc.perform(get("/api/me/check-in/today").session(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.checkInDate").value("2026-10-08"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.monitoringPlanName").value("General Daily Wellness"))
                .andExpect(jsonPath("$.sessionId").value(nullValue()));
        mvc.perform(get("/api/me/check-in/today")).andExpect(status().isUnauthorized());

        long first = start(sarah);
        mvc.perform(get("/api/me/check-in/today").session(sarah))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.sessionId").value(first))
                .andExpect(jsonPath("$.previousDaySession").value(false));
        assertThat(start(sarah)).isEqualTo(first);
        assertThat(sessions.findByPatient_IdAndCheckInDate(sarahId, LocalDate.of(2026, 10, 8))).isPresent();
        assertThat(sessionQuestions.findBySession_IdOrderByDisplayOrderAsc(first)).hasSize(4);

        MonitoringSession open = sessions.findById(first).orElseThrow();
        open.setStatus(SessionStatus.COMPLETED);
        sessions.saveAndFlush(open);
        mvc.perform(get("/api/me/check-in/today").session(sarah))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        assertThat(start(sarah)).isEqualTo(first);

        accounts.saveAndFlush(new UserAccount("doctor1", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("doctor1", "/api/auth/login/clinician");
        long postOpId = plans.findByCode(DemoMonitoringPlans.POST_OPERATIVE).orElseThrow().getId();
        mvc.perform(put("/api/clinician/patients/" + sarahId + "/monitoring-plan").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringPlanId\":" + postOpId + "}"))
                .andExpect(status().isOk());
        assertThat(start(sarah)).isEqualTo(first);
        assertThat(sessions.findById(first).orElseThrow().getMonitoringPlanName()).isEqualTo("General Daily Wellness");

        clock.setInstant(Instant.parse("2026-10-09T15:00:00Z"));
        mvc.perform(get("/api/me/check-in/today").session(sarah))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.checkInDate").value("2026-10-09"))
                .andExpect(jsonPath("$.monitoringPlanName").value("Post-Operative Recovery Demo"));
        long second = start(sarah);
        assertThat(second).isNotEqualTo(first);
        assertThat(sessions.findById(second).orElseThrow().getCheckInDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(sessions.findById(second).orElseThrow().getMonitoringPlanName())
                .isEqualTo("Post-Operative Recovery Demo");
        mvc.perform(post("/api/monitoring/sessions/" + second + "/messages").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I feel okay today.\"}"))
                .andExpect(jsonPath("$.nextQuestion").value("How would you rate your pain related to your recovery today?"));
    }

    @Test
    void anUnfinishedPriorDayIsContinuedAndDoesNotOpenTheNewDate() throws Exception {
        register("ada01", "Ada Lovelace", "America/New_York", "Recovery");
        MockHttpSession ada = login("ada01");
        clock.setInstant(Instant.parse("2026-10-09T03:58:00Z"));
        long october8 = start(ada);
        assertThat(sessions.findById(october8).orElseThrow().getCheckInDate()).isEqualTo(LocalDate.of(2026, 10, 8));

        clock.setInstant(Instant.parse("2026-10-09T04:03:00Z"));
        mvc.perform(get("/api/me/check-in/today").session(ada))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.checkInDate").value("2026-10-08"))
                .andExpect(jsonPath("$.currentDate").value("2026-10-09"))
                .andExpect(jsonPath("$.previousDaySession").value(true))
                .andExpect(jsonPath("$.sessionId").value(october8));
        assertThat(start(ada)).isEqualTo(october8);

        MonitoringSession open = sessions.findById(october8).orElseThrow();
        open.setStatus(SessionStatus.COMPLETED);
        sessions.saveAndFlush(open);
        mvc.perform(get("/api/me/check-in/today").session(ada))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.checkInDate").value("2026-10-09"))
                .andExpect(jsonPath("$.previousDaySession").value(false));
        long october9 = start(ada);
        assertThat(october9).isNotEqualTo(october8);
        assertThat(sessions.findById(october9).orElseThrow().getCheckInDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(sessions.findById(october8).orElseThrow().getCheckInDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void simultaneousStartsCreateOneSession() throws Exception {
        register("race01", "Race Patient", "America/New_York", "Recovery");
        long patientId = patientId(login("race01"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Long> first = pool.submit(() -> startTogether(patientId, ready, go));
            Future<Long> second = pool.submit(() -> startTogether(patientId, ready, go));
            ready.await();
            go.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        } finally {
            pool.shutdownNow();
        }
        assertThat(sessions.findByPatient_IdAndCheckInDate(patientId, LocalDate.of(2026, 10, 8))).isPresent();
        assertThat(sessionQuestions.findBySession_IdOrderByDisplayOrderAsc(
                sessions.findByPatient_IdAndCheckInDate(patientId, LocalDate.of(2026, 10, 8)).orElseThrow().getId()))
                .hasSize(4);
    }

    @Test
    void clinicianSeesEachPatientsLocalDateAndPatientsCannotUseAnotherIdentity() throws Exception {
        register("east01", "Sarah East", "America/New_York", "Recovery");
        register("west01", "John West", "America/Los_Angeles", "Hypertension");
        clock.setInstant(Instant.parse("2026-10-09T06:30:00Z"));
        accounts.saveAndFlush(new UserAccount("doctor2", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("doctor2", "/api/auth/login/clinician");
        MockHttpSession east = login("east01");

        mvc.perform(get("/api/clinician/patients").session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].todayCheckInDate", hasItem("2026-10-09")))
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].todayStatus", hasItem("NOT_STARTED")))
                .andExpect(jsonPath("$[?(@.fullName == 'John West')].todayCheckInDate", hasItem("2026-10-08")))
                .andExpect(jsonPath("$[?(@.fullName == 'John West')].timezone", hasItem("America/Los_Angeles")));
        mvc.perform(get("/api/me/check-in/today").session(doctor)).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/check-in/today").session(doctor).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/check-in/today").with(csrf())).andExpect(status().isUnauthorized());

        long eastSession = start(east);
        MockHttpSession west = login("west01");
        mvc.perform(get("/api/me/check-in/today").session(west))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.sessionId").value(nullValue()));
        assertThat(start(west)).isNotEqualTo(eastSession);
    }

    @Test
    void aLegacySessionWithoutADateDoesNotBlockToday() throws Exception {
        register("legacy01", "Legacy Patient", "UTC", "Recovery");
        MockHttpSession patient = login("legacy01");
        long patientId = patientId(patient);
        MonitoringSession legacy = new MonitoringSession(patients.findById(patientId).orElseThrow());
        legacy.setNextQuestion("Old question");
        sessions.saveAndFlush(legacy);

        mvc.perform(get("/api/me/check-in/today").session(patient))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
        long today = start(patient);
        assertThat(today).isNotEqualTo(legacy.getId());
        assertThat(sessions.findById(today).orElseThrow().getCheckInDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(sessions.findById(legacy.getId()).orElseThrow().getCheckInDate()).isNull();
    }

    private Long startTogether(long patientId, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        go.await();
        return dailyCheckIns.startToday(patientId).getId();
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
