package com.carevoice.clinician;

import com.carevoice.auth.AccountRole;
import com.carevoice.auth.UserAccount;
import com.carevoice.auth.UserAccountRepository;
import com.carevoice.checkin.MutableClock;
import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.InputMode;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.MonitoringTurn;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.history.ExtractedFactsJson;
import com.carevoice.agent.ExtractedClinicalFacts;
import com.carevoice.plan.DemoMonitoringPlans;
import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringPlanRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.repository.PatientRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(ClinicianDashboardFlowTest.FixedClock.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:clinician-dashboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class ClinicianDashboardFlowTest {
    private static final String PASSWORD = "correct-horse-battery";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-08T16:00:00Z"));
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired MutableClock clock;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired PatientRepository patients;
    @Autowired MonitoringSessionRepository sessions;
    @Autowired MonitoringTurnRepository turns;
    @Autowired ClinicalNoteRepository notes;
    @Autowired MonitoringPlanRepository plans;
    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.setInstant(Instant.parse("2026-10-08T16:00:00Z"));
    }

    @Test
    void clinicianDashboardListsTodayAcrossTimezonesAndHidesItFromPatients() throws Exception {
        register("dash-ny", "Sarah East", "America/New_York", "Post-operative recovery");
        register("dash-la", "John West", "America/Los_Angeles", "Hypertension");
        register("dash-new", "New Patient", "UTC", "Recovery");
        accounts.saveAndFlush(new UserAccount("dash-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("dash-doc", "/api/auth/login/clinician");
        MockHttpSession ny = login("dash-ny");
        clock.setInstant(Instant.parse("2026-10-09T06:30:00Z"));

        mvc.perform(get("/api/clinician/patients")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clinician/patients").session(ny)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/patients").session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].timezone", hasItem("America/New_York")))
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].todayCheckInDate", hasItem("2026-10-09")))
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].todayStatus", hasItem("NOT_STARTED")))
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].monitoringPlanName", hasItem("General Daily Wellness")))
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].latestMonitoringFlag", hasItem(nullValue())))
                .andExpect(jsonPath("$[?(@.fullName == 'Sarah East')].requiresReview", hasItem(false)))
                .andExpect(jsonPath("$[?(@.fullName == 'John West')].todayCheckInDate", hasItem("2026-10-08")))
                .andExpect(jsonPath("$[?(@.fullName == 'John West')].todayStatus", hasItem("NOT_STARTED")))
                .andExpect(jsonPath("$[?(@.fullName == 'New Patient')].todaySessionId", hasItem(nullValue())))
                .andExpect(jsonPath("$[?(@.fullName == 'New Patient')].todayStatus", hasItem("NOT_STARTED")));

        clock.setInstant(Instant.parse("2026-10-08T16:00:00Z"));
        register("dash-review", "Review Patient", "America/New_York", "Recovery");
        MockHttpSession review = login("dash-review");
        long sessionId = start(review);
        MonitoringSession session = sessions.findById(sessionId).orElseThrow();
        session.setStatus(SessionStatus.READY_FOR_REVIEW);
        session.setRiskLevel(RiskLevel.RED);
        session.setEscalationReason("Patient reported shortness of breath; clinician review required by configured safety rule.");
        sessions.saveAndFlush(session);
        mvc.perform(get("/api/clinician/patients").session(doctor))
                .andExpect(jsonPath("$[?(@.fullName == 'Review Patient')].todayStatus", hasItem("READY_FOR_REVIEW")))
                .andExpect(jsonPath("$[?(@.fullName == 'Review Patient')].requiresReview", hasItem(true)))
                .andExpect(jsonPath("$[?(@.fullName == 'Review Patient')].latestMonitoringFlag", hasItem("RED")))
                .andExpect(jsonPath("$[?(@.fullName == 'Review Patient')].todaySessionId", hasItem((int) sessionId)));
    }

    @Test
    void clinicianPatientDetailUsesDailyStatusAndKeepsTheSessionSnapshot() throws Exception {
        register("detail-patient", "Sarah Miller", "America/New_York", "Post-operative recovery");
        accounts.saveAndFlush(new UserAccount("detail-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("detail-doc", "/api/auth/login/clinician");
        MockHttpSession sarah = login("detail-patient");
        long sarahId = patientId(sarah);

        mvc.perform(get("/api/clinician/patients/" + sarahId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clinician/patients/" + sarahId).session(sarah)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/patients/999999").session(doctor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clinician/patients/" + sarahId).session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.monitoringFlag").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.questionsAnswered").value(0))
                .andExpect(jsonPath("$.activeMonitoringPlanName").value("General Daily Wellness"))
                .andExpect(jsonPath("$.reminderEnabled").value(true))
                .andExpect(jsonPath("$.reminderTime").value("08:00"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));

        long sessionId = start(sarah);
        mvc.perform(get("/api/clinician/patients/" + sarahId).session(doctor))
                .andExpect(jsonPath("$.todayStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.todaySessionId").value(sessionId))
                .andExpect(jsonPath("$.sessionPlanName").value("General Daily Wellness"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.monitoringFlag").value("GREEN"));

        long hypertensionId = plans.findByCode(DemoMonitoringPlans.HYPERTENSION).orElseThrow().getId();
        mvc.perform(put("/api/clinician/patients/" + sarahId + "/monitoring-plan").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringPlanId\":" + hypertensionId + "}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clinician/patients/" + sarahId).session(doctor))
                .andExpect(jsonPath("$.activeMonitoringPlanName").value("Hypertension Symptom Monitoring Demo"))
                .andExpect(jsonPath("$.sessionPlanName").value("General Daily Wellness"));

        MonitoringSession completed = sessions.findById(sessionId).orElseThrow();
        completed.setStatus(SessionStatus.COMPLETED);
        sessions.saveAndFlush(completed);
        mvc.perform(get("/api/clinician/patients/" + sarahId).session(doctor))
                .andExpect(jsonPath("$.todayStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.requiresReview").value(false));

        completed.setStatus(SessionStatus.READY_FOR_REVIEW);
        completed.setRiskLevel(RiskLevel.YELLOW);
        completed.setEscalationReason("Pain score is at or above the configured review threshold (7/10).");
        sessions.saveAndFlush(completed);
        mvc.perform(get("/api/clinician/patients/" + sarahId).session(doctor))
                .andExpect(jsonPath("$.todayStatus").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.requiresReview").value(true))
                .andExpect(jsonPath("$.monitoringFlag").value("YELLOW"))
                .andExpect(jsonPath("$.escalationReason").value("Pain score is at or above the configured review threshold (7/10)."));
    }

    @Test
    void clinicianSessionDetailShowsTurnsNotesAndFactsWithoutProviderInternals() throws Exception {
        register("session-a", "Sarah Miller", "America/New_York", "Post-operative recovery");
        register("session-b", "John Smith", "America/Chicago", "Hypertension");
        accounts.saveAndFlush(new UserAccount("session-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("session-doc", "/api/auth/login/clinician");
        MockHttpSession sarah = login("session-a");
        MockHttpSession john = login("session-b");
        long sessionId = start(sarah);
        MonitoringSession session = sessions.findById(sessionId).orElseThrow();
        session.setStatus(SessionStatus.READY_FOR_REVIEW);
        session.setRiskLevel(RiskLevel.RED);
        session.setPainScore(5);
        session.setMedicationTaken(null);
        session.setShortnessOfBreath(false);
        session.setTemperature(98.7);
        session.setEscalationReason("Patient reported shortness of breath; clinician review required by configured safety rule.");
        sessions.saveAndFlush(session);

        MonitoringTurn voice = turns.save(new MonitoringTurn(
                session.getPatient(), session, 1,
                "Tell me how you are feeling today in your own words.",
                "I've been dizzy since this morning.",
                InputMode.VOICE));
        ClinicalNote noted = new ClinicalNote(
                voice,
                ExtractedFactsJson.write(new ExtractedClinicalFacts(null, true, "This morning", null, null, null, null, null, null)),
                RiskLevel.YELLOW,
                null);
        noted.setNoteText("Patient reports dizziness beginning this morning.");
        noted.setNoteProvider("gemini");
        noted.setModel("gemini-secret-model");
        notes.save(noted);
        turns.save(new MonitoringTurn(
                session.getPatient(), session, 2,
                "Did you lose consciousness?",
                "No.",
                InputMode.TEXT));
        MonitoringTurn pending = turns.save(new MonitoringTurn(
                session.getPatient(), session, 3, "How is your sleep?", "Poor.", InputMode.TEXT));
        notes.save(new ClinicalNote(pending, "{}", RiskLevel.GREEN, null));

        mvc.perform(get("/api/clinician/sessions/" + sessionId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clinician/sessions/" + sessionId).session(sarah)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/sessions/" + sessionId).session(john)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/sessions/999999").session(doctor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clinician/sessions/" + sessionId).session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId(sarah)))
                .andExpect(jsonPath("$.checkInDate").value("2026-10-08"))
                .andExpect(jsonPath("$.monitoringPlanName").value("General Daily Wellness"))
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.monitoringFlag").value("RED"))
                .andExpect(jsonPath("$.escalationReason").value("Patient reported shortness of breath; clinician review required by configured safety rule."))
                .andExpect(jsonPath("$.turns[0].sequence").value(1))
                .andExpect(jsonPath("$.turns[0].inputMode").value("VOICE"))
                .andExpect(jsonPath("$.turns[0].clinicalNote.noteText").value("Patient reports dizziness beginning this morning."))
                .andExpect(jsonPath("$.turns[0].clinicalNote.noteLabel").value("AI-assisted CareVoice Note"))
                .andExpect(jsonPath("$.turns[0].clinicalNote.facts[?(@.label == 'Dizziness')].value", hasItem("Yes")))
                .andExpect(jsonPath("$.turns[1].sequence").value(2))
                .andExpect(jsonPath("$.turns[1].inputMode").value("TEXT"))
                .andExpect(jsonPath("$.turns[1].clinicalNote").value(nullValue()))
                .andExpect(jsonPath("$.turns[2].clinicalNote.noteText").value(nullValue()))
                .andExpect(jsonPath("$.sessionFacts[?(@.label == 'Pain score')].value", hasItem("5 / 10")))
                .andExpect(jsonPath("$.sessionFacts[?(@.label == 'Shortness of breath')].value", hasItem("No")))
                .andExpect(jsonPath("$.sessionFacts[?(@.label == 'Temperature')].value", hasItem("98.7")))
                .andExpect(jsonPath("$.sessionFacts[?(@.label == 'Medication taken')]").isEmpty())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("gemini-secret-model"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("audio"))));
    }

    @Test
    void clinicianHistoryKeepsLegacySessionsAndCheckInDates() throws Exception {
        register("hist-patient", "Legacy Patient", "UTC", "Recovery");
        accounts.saveAndFlush(new UserAccount("hist-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("hist-doc", "/api/auth/login/clinician");
        MockHttpSession patient = login("hist-patient");
        long patientId = patientId(patient);
        MonitoringSession legacy = sessions.saveAndFlush(new MonitoringSession(patients.findById(patientId).orElseThrow()));
        long todayId = start(patient);

        mvc.perform(get("/api/patients/" + patientId + "/history").session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].sessionId").value(todayId))
                .andExpect(jsonPath("$.sessions[0].checkInDate").value("2026-10-08"))
                .andExpect(jsonPath("$.sessions[0].monitoringPlanName").value("General Daily Wellness"))
                .andExpect(jsonPath("$.sessions[?(@.sessionId == " + legacy.getId() + ")].checkInDate", hasItem(nullValue())));
        mvc.perform(get("/api/patients/" + patientId + "/longitudinal-summary?limit=30").session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId));
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
