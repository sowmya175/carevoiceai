package com.carevoice.plan;
import com.carevoice.service.DemoMonitoringPlans;

import com.carevoice.domain.AccountRole;
import com.carevoice.domain.UserAccount;
import com.carevoice.repository.UserAccountRepository;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.SessionStatus;
import com.carevoice.domain.MonitoringPlanQuestion;
import com.carevoice.domain.PatientMonitoringPlan;
import com.carevoice.domain.SessionPlanQuestion;
import com.carevoice.repository.MonitoringPlanQuestionRepository;
import com.carevoice.repository.MonitoringPlanRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.PatientMonitoringPlanRepository;
import com.carevoice.repository.SessionPlanQuestionRepository;
import com.jayway.jsonpath.JsonPath;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.carevoice.checkin.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(MonitoringPlanFlowTest.FixedClock.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:plans;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class MonitoringPlanFlowTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired WebApplicationContext context;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired MonitoringPlanRepository plans;
    @Autowired MonitoringPlanQuestionRepository questions;
    @Autowired PatientMonitoringPlanRepository assignments;
    @Autowired MonitoringSessionRepository sessions;
    @Autowired SessionPlanQuestionRepository sessionQuestions;
    @Autowired MutableClock clock;
    MockMvc mvc;

    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-08T16:00:00Z"));
        }
    }

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void demoPlansAreSeededInOrderAndMarkedAsNotValidated() {
        var general = plans.findByCode(DemoMonitoringPlans.GENERAL).orElseThrow();
        var postOp = plans.findByCode(DemoMonitoringPlans.POST_OPERATIVE).orElseThrow();
        var hypertension = plans.findByCode(DemoMonitoringPlans.HYPERTENSION).orElseThrow();
        var diabetes = plans.findByCode(DemoMonitoringPlans.DIABETES).orElseThrow();

        assertThat(general.getName()).isEqualTo("General Daily Wellness");
        assertThat(general.getDescription()).contains("Not clinically validated");
        assertThat(questions.findByMonitoringPlan_IdAndActiveTrueOrderByDisplayOrderAsc(general.getId()))
                .extracting(MonitoringPlanQuestion::getMonitoringField)
                .containsExactly(
                        MonitoringField.PAIN_SCORE,
                        MonitoringField.MEDICATION_TAKEN,
                        MonitoringField.APPETITE,
                        MonitoringField.SLEEP_QUALITY);
        assertThat(questions.findByMonitoringPlan_IdAndActiveTrueOrderByDisplayOrderAsc(postOp.getId()))
                .extracting(MonitoringPlanQuestion::getMonitoringField)
                .containsExactly(
                        MonitoringField.PAIN_SCORE,
                        MonitoringField.MEDICATION_TAKEN,
                        MonitoringField.TEMPERATURE,
                        MonitoringField.APPETITE,
                        MonitoringField.SLEEP_QUALITY);
        assertThat(questions.findByMonitoringPlan_IdAndActiveTrueOrderByDisplayOrderAsc(hypertension.getId()))
                .extracting(MonitoringPlanQuestion::getMonitoringField)
                .containsExactly(MonitoringField.MEDICATION_TAKEN, MonitoringField.SLEEP_QUALITY);
        assertThat(hypertension.getDescription()).contains("does not replace blood-pressure");
        assertThat(questions.findByMonitoringPlan_IdAndActiveTrueOrderByDisplayOrderAsc(diabetes.getId()))
                .extracting(MonitoringPlanQuestion::getMonitoringField)
                .containsExactly(
                        MonitoringField.MEDICATION_TAKEN,
                        MonitoringField.APPETITE,
                        MonitoringField.SLEEP_QUALITY);
        assertThat(diabetes.getDescription()).contains("does not replace glucose");
    }

    @Test
    void assignmentSecurityDefaultPlanAndSessionSnapshotStayStable() throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("sarah01", "Sarah Miller", "Post-operative recovery")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("john01", "John Smith", "Hypertension")))
                .andExpect(status().isCreated());

        MockHttpSession sarah = login("sarah01", "/api/auth/login/patient");
        MockHttpSession john = login("john01", "/api/auth/login/patient");
        long sarahId = patientId(sarah);
        long johnId = patientId(john);

        mvc.perform(get("/api/me/monitoring-plan").session(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("General Daily Wellness"))
                .andExpect(jsonPath("$.description", containsString("Not clinically validated")));
        mvc.perform(get("/api/me/monitoring-plan").session(john))
                .andExpect(jsonPath("$.name").value("General Daily Wellness"));
        assertThat(assignments.findByPatient_IdAndActiveTrue(sarahId)).hasSize(1);

        accounts.saveAndFlush(new UserAccount("doctor1", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("doctor1", "/api/auth/login/clinician");
        long postOpId = plans.findByCode(DemoMonitoringPlans.POST_OPERATIVE).orElseThrow().getId();
        long hypertensionId = plans.findByCode(DemoMonitoringPlans.HYPERTENSION).orElseThrow().getId();

        mvc.perform(put("/api/clinician/patients/" + sarahId + "/monitoring-plan").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringPlanId\":" + postOpId + "}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/clinician/patients/" + sarahId + "/monitoring-plan").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringPlanId\":" + postOpId + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/monitoring-plans").session(sarah)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/monitoring-plans")).andExpect(status().isUnauthorized());

        mvc.perform(put("/api/clinician/patients/" + sarahId + "/monitoring-plan").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringPlanId\":" + postOpId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Post-Operative Recovery Demo"));
        assertThat(assignments.findByPatient_IdAndActiveTrue(sarahId)).hasSize(1);
        assertThat(assignments.findByPatient_Id(sarahId))
                .filteredOn(assignment -> !assignment.isActive())
                .isNotEmpty()
                .allSatisfy(assignment -> assertThat(assignment.getEndedAt()).isNotNull());

        long sarahSession = startSession(sarah);
        mvc.perform(post("/api/monitoring/sessions/" + sarahSession + "/messages").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I feel okay today.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedField").value("PAIN_SCORE"))
                .andExpect(jsonPath("$.nextQuestion").value("How would you rate your pain related to your recovery today?"))
                .andExpect(jsonPath("$.missingFields", hasItem("TEMPERATURE")));

        long johnSession = startSession(john);
        mvc.perform(post("/api/monitoring/sessions/" + johnSession + "/messages").session(john).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I feel okay today.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedField").value("PAIN_SCORE"))
                .andExpect(jsonPath("$.nextQuestion").value("On a scale from 0 to 10, how would you rate your pain today?"))
                .andExpect(jsonPath("$.missingFields", not(hasItem("TEMPERATURE"))));

        mvc.perform(put("/api/clinician/patients/" + sarahId + "/monitoring-plan").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringPlanId\":" + hypertensionId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hypertension Symptom Monitoring Demo"));
        assertThat(assignments.findByPatient_IdAndActiveTrue(sarahId)).singleElement()
                .extracting(PatientMonitoringPlan::getMonitoringPlan)
                .extracting(plan -> plan.getCode())
                .isEqualTo(DemoMonitoringPlans.HYPERTENSION);

        mvc.perform(get("/api/monitoring/sessions/" + sarahSession + "/history").session(sarah))
                .andExpect(jsonPath("$.monitoringPlanName").value("Post-Operative Recovery Demo"));
        assertThat(sessionQuestions.findBySession_IdOrderByDisplayOrderAsc(sarahSession))
                .extracting(SessionPlanQuestion::getMonitoringField)
                .containsExactly(
                        MonitoringField.PAIN_SCORE,
                        MonitoringField.MEDICATION_TAKEN,
                        MonitoringField.TEMPERATURE,
                        MonitoringField.APPETITE,
                        MonitoringField.SLEEP_QUALITY);

        mvc.perform(post("/api/monitoring/sessions/" + sarahSession + "/messages").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"My pain is 4.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedField").value("MEDICATION_TAKEN"))
                .andExpect(jsonPath("$.missingFields", hasItem("TEMPERATURE")));

        MonitoringSession open = sessions.findById(sarahSession).orElseThrow();
        open.setStatus(SessionStatus.COMPLETED);
        sessions.saveAndFlush(open);
        clock.setInstant(Instant.parse("2026-10-09T16:00:00Z"));
        long nextSession = startSession(sarah);
        mvc.perform(post("/api/monitoring/sessions/" + nextSession + "/messages").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I feel okay today.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedField").value("MEDICATION_TAKEN"))
                .andExpect(jsonPath("$.missingFields", not(hasItem("PAIN_SCORE"))))
                .andExpect(jsonPath("$.missingFields", not(hasItem("TEMPERATURE"))));
        mvc.perform(get("/api/monitoring/sessions/" + nextSession + "/history").session(sarah))
                .andExpect(jsonPath("$.monitoringPlanName").value("Hypertension Symptom Monitoring Demo"));
        mvc.perform(get("/api/monitoring/sessions/" + sarahSession + "/history").session(sarah))
                .andExpect(jsonPath("$.monitoringPlanName").value("Post-Operative Recovery Demo"));
        assertThat(sessions.findById(sarahSession).orElseThrow().getMonitoringPlanName())
                .isEqualTo("Post-Operative Recovery Demo");
    }

    private long startSession(MockHttpSession session) throws Exception {
        MvcResult started = mvc.perform(post("/api/monitoring/me/sessions").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextQuestion").value("Tell me how you are feeling today in your own words."))
                .andReturn();
        return ((Number) JsonPath.read(started.getResponse().getContentAsString(), "$.sessionId")).longValue();
    }

    private long patientId(MockHttpSession session) throws Exception {
        MvcResult me = mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.patient.id")).longValue();
    }

    private MockHttpSession login(String username, String path) throws Exception {
        MvcResult result = mvc.perform(post(path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String registration(String username, String name, String condition) {
        return """
                {"username":"%s","password":"%s","fullName":"%s","medicalCondition":"%s","timezone":"America/New_York"}
                """.formatted(username, PASSWORD, name, condition);
    }
}
