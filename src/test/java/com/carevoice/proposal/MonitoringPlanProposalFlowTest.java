package com.carevoice.proposal;
import com.carevoice.service.MonitoringPlanProposalService;
import com.carevoice.domain.Patient;

import com.carevoice.domain.AccountRole;
import com.carevoice.domain.UserAccount;
import com.carevoice.repository.UserAccountRepository;
import com.carevoice.checkin.MutableClock;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringPlanQuestion;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.ProposalStatus;
import com.carevoice.domain.SessionStatus;
import com.carevoice.service.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.repository.MonitoringPlanQuestionRepository;
import com.carevoice.repository.MonitoringPlanRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.PatientRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
@Import(MonitoringPlanProposalFlowTest.FixedClock.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-proposals;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class MonitoringPlanProposalFlowTest {
    private static final String PASSWORD = "correct-horse-battery";

    @org.springframework.boot.test.context.TestConfiguration
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
    @Autowired MonitoringPlanProposalService proposals;
    @Autowired com.carevoice.repository.MonitoringPlanProposalRepository proposalRows;
    @Autowired MonitoringPlanRepository plans;
    @Autowired MonitoringPlanQuestionRepository planQuestions;
    @Autowired MonitoringSessionRepository sessions;
    MockMvc mvc;
    MockHttpSession doctor;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.setInstant(Instant.parse("2026-10-08T16:00:00Z"));
        if (accounts.findByUsername("proposal-doc").isEmpty()) {
            accounts.saveAndFlush(new UserAccount("proposal-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        }
        doctor = login("proposal-doc", "/api/auth/login/clinician");
    }

    @Test
    void planAskableFieldsExcludeExtractionOnlyAndCatalogOnly() throws Exception {
        mvc.perform(get("/api/clinician/monitoring-fields").session(doctor).param("planAskable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("PAIN_SCORE")))
                .andExpect(jsonPath("$[*].code", hasItem("MEDICATION_TAKEN")))
                .andExpect(jsonPath("$[*].code", not(hasItem("DIZZINESS"))))
                .andExpect(jsonPath("$[*].code", not(hasItem("SHORTNESS_OF_BREATH"))))
                .andExpect(jsonPath("$[*].code", not(hasItem("INCISION_STATUS"))))
                .andExpect(jsonPath("$[*].code", not(hasItem("SWELLING"))))
                .andExpect(jsonPath("$[*].code", not(hasItem("MOBILITY"))))
                .andExpect(jsonPath("$[*].code", not(hasItem("ACTIVITY_TOLERANCE"))));
    }

    @Test
    void unknownFieldIsRejectedAndUnsupportedFieldsAreNotStoredAsQuestions() throws Exception {
        long patientId = register("proposal-unknown", "Unknown Patient");
        long accountId = accounts.findByUsername("proposal-doc").orElseThrow().getId();
        assertThatThrownBy(() -> proposals.store(patientId, accountId, new PlanGenerationResult(List.of(), List.of(
                new GeneratedQuestion("UNKNOWN_HEART_RECOVERY_SCORE", "How is the new score?", true, List.of(), null),
                new GeneratedQuestion("MEDICATION_TAKEN", "Have you taken your prescribed medication today?", true, List.of(), null)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not recognize");
        assertThat(proposalRows.findByPatient_IdOrderByCreatedAtDesc(patientId)).isEmpty();
        mvc.perform(get("/api/clinician/patients/" + patientId + "/monitoring-plan").session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("General Daily Wellness"));

        long kept = proposals.store(patientId, accountId, new PlanGenerationResult(
                List.of("not a code", "CARDIAC_SURGERY_RECOVERY"),
                List.of(
                        new GeneratedQuestion("INCISION_STATUS", "How does the incision look?", true, List.of(), null),
                        new GeneratedQuestion("DIZZINESS", "Are you dizzy?", true, List.of(), null),
                        new GeneratedQuestion("MEDICATION_TAKEN", "Have you taken your prescribed medication today?", true, List.of(), "First"),
                        new GeneratedQuestion("MEDICATION_TAKEN", "", false, List.of(), "Second"),
                        new GeneratedQuestion("PAIN_SCORE", "On a scale from 0 to 10, how would you rate your pain today?", true, List.of(), null))))
                .id();
        mvc.perform(get("/api/clinician/monitoring-plan-proposals/" + kept).session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.conditionFamilies", hasItem("CARDIAC_SURGERY_RECOVERY")))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("INCISION_STATUS"))))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("DIZZINESS"))))
                .andExpect(jsonPath("$.questions[?(@.fieldCode == 'MEDICATION_TAKEN')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.questions[?(@.fieldCode == 'MEDICATION_TAKEN')].required", hasItem(true)))
                .andExpect(jsonPath("$.questions[?(@.fieldCode == 'INCISION_STATUS')]").isEmpty())
                .andExpect(jsonPath("$.questions[?(@.fieldCode == 'PAIN_SCORE')]").value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void generationSnapshotsConditionsAndRegenerationKeepsThePreviousDraft() throws Exception {
        long patientId = register("proposal-history", "History Patient");
        MockHttpSession patient = login("proposal-history", "/api/auth/login/patient");
        long primaryId = conditionId(patientId, "History Patient condition");
        mvc.perform(put("/api/clinician/patients/" + patientId + "/conditions/" + primaryId).session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("Open-heart surgery recovery", "POST_OPERATIVE", true, true)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/clinician/patients/" + patientId + "/conditions").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Hypertension", "HYPERTENSION", false)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/clinician/patients/" + patientId + "/conditions").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Type 2 diabetes", "DIABETES", false)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/clinician/patients/" + patientId + "/monitoring-plan-proposals")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/clinician/patients/" + patientId + "/monitoring-plan-proposals").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clinician/patients/" + patientId + "/monitoring-plan-proposals").session(patient))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clinician/patients/" + patientId + "/monitoring-plan-proposals").session(patient).with(csrf()))
                .andExpect(status().isForbidden());

        MvcResult created = mvc.perform(post("/api/clinician/patients/" + patientId + "/monitoring-plan-proposals")
                        .session(doctor).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.conditions[?(@.primaryCondition == true)].conditionName", hasItem("Open-heart surgery recovery")))
                .andExpect(jsonPath("$.conditions[?(@.conditionName == 'Hypertension')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.conditions[?(@.conditionName == 'Type 2 diabetes')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.questions[?(@.fieldCode == 'MEDICATION_TAKEN')]").value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.questions[?(@.fieldCode == 'INCISION_STATUS')]").isEmpty())
                .andReturn();
        String createdBody = created.getResponse().getContentAsString();
        long proposalId = number(createdBody, "$.id");
        long medicationId = number(createdBody, "$.questions[?(@.fieldCode == 'MEDICATION_TAKEN')].id");

        mvc.perform(put("/api/clinician/patients/" + patientId + "/conditions/" + primaryId).session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("Renamed recovery", "POST_OPERATIVE", true, true)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clinician/monitoring-plan-proposals/" + proposalId).session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions[?(@.primaryCondition == true)].conditionName", hasItem("Open-heart surgery recovery")));

        mvc.perform(put("/api/clinician/monitoring-plan-proposals/" + proposalId + "/questions/" + medicationId)
                        .session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionText":"Have you taken your prescribed medications today?","required":true,"enabled":true,"fieldCode":"PAIN_SCORE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[?(@.id == %d)].fieldCode".formatted(medicationId), hasItem("MEDICATION_TAKEN")))
                .andExpect(jsonPath("$.questions[?(@.id == %d)].questionText".formatted(medicationId),
                        hasItem("Have you taken your prescribed medications today?")));

        MvcResult regenerated = mvc.perform(post("/api/clinician/patients/" + patientId + "/monitoring-plan-proposals")
                        .session(doctor).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conditions[?(@.primaryCondition == true)].conditionName", hasItem("Renamed recovery")))
                .andReturn();
        long secondId = number(regenerated.getResponse().getContentAsString(), "$.id");
        assertThat(secondId).isNotEqualTo(proposalId);
        mvc.perform(get("/api/clinician/monitoring-plan-proposals/" + proposalId).session(doctor))
                .andExpect(jsonPath("$.status").value("SUPERSEDED"));
        mvc.perform(post("/api/clinician/monitoring-plan-proposals/" + proposalId + "/approve").session(doctor).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/clinician/monitoring-plan-proposals/" + secondId + "/reject").session(doctor).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/api/clinician/monitoring-plan-proposals/" + secondId + "/approve").session(doctor).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/clinician/patients/" + patientId + "/monitoring-plan").session(doctor))
                .andExpect(jsonPath("$.name").value("General Daily Wellness"));
        assertThat(proposalRows.findByPatient_IdOrderByCreatedAtDesc(patientId))
                .extracting(row -> row.getStatus())
                .contains(ProposalStatus.SUPERSEDED, ProposalStatus.REJECTED);
    }

    @Test
    void approvalAssignsAPatientSpecificPlanWithoutChangingTheOpenSession() throws Exception {
        long patientId = register("proposal-approve", "Approve Patient");
        MockHttpSession patient = login("proposal-approve", "/api/auth/login/patient");
        long otherId = register("proposal-other", "Other Patient");
        long primaryId = conditionId(patientId, "Approve Patient condition");
        mvc.perform(put("/api/clinician/patients/" + patientId + "/conditions/" + primaryId).session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("Open-heart surgery recovery", "POST_OPERATIVE", true, true)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/clinician/patients/" + patientId + "/conditions").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Hypertension", "HYPERTENSION", false)))
                .andExpect(status().isCreated());

        MvcResult started = mvc.perform(post("/api/me/check-in/today").session(patient).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = ((Number) JsonPath.read(started.getResponse().getContentAsString(), "$.sessionId")).longValue();

        MvcResult created = mvc.perform(post("/api/clinician/patients/" + patientId + "/monitoring-plan-proposals")
                        .session(doctor).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        long proposalId = number(body, "$.id");
        long painId = number(body, "$.questions[?(@.fieldCode == 'PAIN_SCORE')].id");
        long temperatureId = number(body, "$.questions[?(@.fieldCode == 'TEMPERATURE')].id");

        mvc.perform(put("/api/clinician/monitoring-plan-proposals/" + proposalId + "/questions/" + painId)
                        .session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionText":"On a scale from 0 to 10, what is your pain level today?","required":true,"enabled":true}
                                """))
                .andExpect(status().isOk());
        mvc.perform(put("/api/clinician/monitoring-plan-proposals/" + proposalId + "/questions/" + temperatureId)
                        .session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionText":"What is your temperature reading?","required":true,"enabled":false}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/clinician/monitoring-plan-proposals/" + proposalId + "/questions")
                        .session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldCode":"DIZZINESS_ONSET","questionText":"When did the dizziness start?","required":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions[?(@.fieldCode == 'DIZZINESS_ONSET')]").value(org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(post("/api/clinician/monitoring-plan-proposals/" + proposalId + "/questions")
                        .session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldCode":"INCISION_STATUS","questionText":"How does the incision look?","required":true}
                                """))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/clinician/monitoring-plan-proposals/" + proposalId + "/questions")
                        .session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldCode":"NEW_SCORE","questionText":"What is the new score?","required":true}
                                """))
                .andExpect(status().isUnprocessableContent());

        MvcResult current = mvc.perform(get("/api/clinician/monitoring-plan-proposals/" + proposalId).session(doctor))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> order = JsonPath.read(current.getResponse().getContentAsString(), "$.questions[*].id");
        List<Integer> reversed = order.reversed();
        String ids = reversed.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
        mvc.perform(put("/api/clinician/monitoring-plan-proposals/" + proposalId + "/question-order")
                        .session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":[" + ids + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].id").value(reversed.getFirst()));

        MvcResult approved = mvc.perform(post("/api/clinician/monitoring-plan-proposals/" + proposalId + "/approve")
                        .session(doctor).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedMonitoringPlanName").value("Personalized Daily Monitoring Plan"))
                .andReturn();
        long planId = ((Number) JsonPath.read(approved.getResponse().getContentAsString(), "$.approvedMonitoringPlanId")).longValue();
        var plan = plans.findById(planId).orElseThrow();
        assertThat(plan.getOwnerPatientId()).isEqualTo(patientId);
        assertThat(plan.getName()).isEqualTo("Personalized Daily Monitoring Plan");
        List<MonitoringPlanQuestion> questions = planQuestions.findByMonitoringPlan_IdAndActiveTrueOrderByDisplayOrderAsc(planId);
        assertThat(questions).extracting(MonitoringPlanQuestion::getMonitoringField)
                .contains(MonitoringField.PAIN_SCORE, MonitoringField.DIZZINESS_ONSET)
                .doesNotContain(MonitoringField.TEMPERATURE);
        assertThat(questions).filteredOn(question -> question.getMonitoringField() == MonitoringField.PAIN_SCORE)
                .singleElement()
                .extracting(MonitoringPlanQuestion::getQuestionTemplate)
                .isEqualTo("On a scale from 0 to 10, what is your pain level today?");

        mvc.perform(get("/api/me/check-in/today").session(patient))
                .andExpect(jsonPath("$.monitoringPlanName").value("General Daily Wellness"));
        mvc.perform(get("/api/me/monitoring-plan").session(patient))
                .andExpect(jsonPath("$.name").value("Personalized Daily Monitoring Plan"));
        mvc.perform(get("/api/clinician/monitoring-plans").session(doctor))
                .andExpect(jsonPath("$[*].name", not(hasItem("Personalized Daily Monitoring Plan"))));
        mvc.perform(put("/api/clinician/patients/" + otherId + "/monitoring-plan").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringPlanId\":" + planId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("That monitoring plan is not available."));

        MonitoringSession open = sessions.findById(sessionId).orElseThrow();
        assertThat(open.getMonitoringPlanName()).isEqualTo("General Daily Wellness");
        open.setStatus(SessionStatus.COMPLETED);
        sessions.saveAndFlush(open);
        clock.setInstant(Instant.parse("2026-10-09T16:00:00Z"));
        mvc.perform(post("/api/me/check-in/today").session(patient).with(csrf())).andExpect(status().isOk());
        MonitoringSession next = sessions.findByPatient_IdAndCheckInDate(patientId, LocalDate.of(2026, 10, 9)).orElseThrow();
        assertThat(next.getMonitoringPlanName()).isEqualTo("Personalized Daily Monitoring Plan");
        assertThat(sessions.findById(sessionId).orElseThrow().getMonitoringPlanName()).isEqualTo("General Daily Wellness");
    }

    private static long number(String json, String path) {
        Object value = JsonPath.read(json, path);
        if (value instanceof List<?> list) {
            value = list.getFirst();
        }
        return ((Number) value).longValue();
    }

    private long register(String username, String name) throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","fullName":"%s","medicalCondition":"%s condition","timezone":"America/New_York"}
                                """.formatted(username, PASSWORD, name, name)))
                .andExpect(status().isCreated());
        return patients.findAll().stream()
                .filter(patient -> name.equals(patient.getFullName()))
                .findFirst().orElseThrow().getId();
    }

    private long conditionId(long patientId, String name) throws Exception {
        MvcResult result = mvc.perform(get("/api/clinician/patients/" + patientId + "/conditions").session(doctor))
                .andExpect(status().isOk())
                .andReturn();
        return number(result.getResponse().getContentAsString(),
                "$.[?(@.conditionName == '" + name + "')].id");
    }

    private MockHttpSession login(String username, String path) throws Exception {
        MvcResult result = mvc.perform(post(path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String condition(String name, String category, boolean primary) {
        return """
                {"conditionName":"%s","monitoringCategory":"%s","primaryCondition":%s}
                """.formatted(name, category, primary);
    }

    private static String update(String name, String category, boolean active, boolean primary) {
        return """
                {"conditionName":"%s","monitoringCategory":"%s","active":%s,"primaryCondition":%s}
                """.formatted(name, category, active, primary);
    }
}
