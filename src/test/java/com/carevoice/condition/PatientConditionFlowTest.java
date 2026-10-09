package com.carevoice.condition;
import com.carevoice.service.PatientConditionBootstrap;

import com.carevoice.domain.AccountRole;
import com.carevoice.domain.UserAccount;
import com.carevoice.repository.UserAccountRepository;
import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.Patient;
import com.carevoice.repository.PatientConditionRepository;
import com.carevoice.repository.PatientRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:patient-conditions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class PatientConditionFlowTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired WebApplicationContext context;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired PatientRepository patients;
    @Autowired PatientConditionRepository conditions;
    @Autowired PatientConditionBootstrap bootstrap;
    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registrationCreatesOneOtherPrimaryConditionAndBootstrapDoesNotDuplicateIt() throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("cond-reg", "Sarah", "Open heart surgery recovery", "America/New_York")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patient.medicalCondition").value("Open heart surgery recovery"));
        long patientId = patients.findAll().stream()
                .filter(patient -> "Sarah".equals(patient.getFullName()))
                .findFirst().orElseThrow().getId();
        assertThat(conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId)).hasSize(1);
        var created = conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId).getFirst();
        assertThat(created.getConditionName()).isEqualTo("Open heart surgery recovery");
        assertThat(created.getMonitoringCategory()).isEqualTo(MonitoringCategory.OTHER);
        assertThat(created.isPrimaryCondition()).isTrue();
        assertThat(created.isActive()).isTrue();

        bootstrap.run(null);
        bootstrap.run(null);
        assertThat(conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId)).hasSize(1);
    }

    @Test
    void legacyMedicalConditionBecomesOneOtherConditionAndStaysOne() throws Exception {
        Patient legacy = patients.saveAndFlush(new Patient(
                "Legacy Patient", "Open heart surgery post operation", "America/New_York"));
        assertThat(conditions.existsByPatient_Id(legacy.getId())).isFalse();

        bootstrap.run(null);
        bootstrap.run(null);

        var rows = conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(legacy.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getConditionName()).isEqualTo("Open heart surgery post operation");
        assertThat(rows.getFirst().getMonitoringCategory()).isEqualTo(MonitoringCategory.OTHER);
        assertThat(rows.getFirst().isPrimaryCondition()).isTrue();
        assertThat(rows.getFirst().isActive()).isTrue();

        Patient blank = patients.saveAndFlush(new Patient("No Condition", "Daily monitoring"));
        bootstrap.run(null);
        assertThat(conditions.existsByPatient_Id(blank.getId())).isFalse();
    }

    @Test
    void clinicianManagesSeveralConditionsWithoutChangingTheMonitoringPlan() throws Exception {
        register("cond-sarah", "Sarah Miller", "Open-heart surgery recovery");
        register("cond-john", "John Smith", "Hypertension");
        accounts.saveAndFlush(new UserAccount("cond-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = login("cond-doc", "/api/auth/login/clinician");
        MockHttpSession sarah = login("cond-sarah");
        MockHttpSession john = login("cond-john");
        long sarahId = patientId(sarah);
        long johnId = patientId(john);

        mvc.perform(get("/api/clinician/patients/" + sarahId + "/conditions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/clinician/patients/" + sarahId + "/conditions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Hypertension", "HYPERTENSION", false)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/conditions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/clinician/patients/" + sarahId + "/conditions").session(sarah).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Hypertension", "HYPERTENSION", false)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/patients/999999/conditions").session(doctor)).andExpect(status().isNotFound());

        long primaryId = conditionId(sarahId, "Open-heart surgery recovery");
        MvcResult hypertension = mvc.perform(post("/api/clinician/patients/" + sarahId + "/conditions").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Hypertension", "HYPERTENSION", false)))
                .andExpect(status().isCreated())
                .andReturn();
        long hypertensionId = ((Number) JsonPath.read(hypertension.getResponse().getContentAsString(), "$.id")).longValue();
        MvcResult diabetes = mvc.perform(post("/api/clinician/patients/" + sarahId + "/conditions").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Type 2 diabetes", "DIABETES", false)))
                .andExpect(status().isCreated())
                .andReturn();
        long diabetesId = ((Number) JsonPath.read(diabetes.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(post("/api/clinician/patients/" + sarahId + "/conditions").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition(" hypertension ", "HYPERTENSION", false)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/clinician/patients/" + sarahId + "/conditions").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(condition("Pulmonary hypertension", "HYPERTENSION", false)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/clinician/patients/" + sarahId + "/conditions").session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.primaryCondition == true)].conditionName", hasItem("Open-heart surgery recovery")))
                .andExpect(jsonPath("$[?(@.conditionName == 'Hypertension')].monitoringCategory", hasItem("HYPERTENSION")))
                .andExpect(jsonPath("$[?(@.conditionName == 'Type 2 diabetes')].monitoringCategory", hasItem("DIABETES")))
                .andExpect(jsonPath("$[?(@.conditionName == 'Pulmonary hypertension')].conditionName", hasItem("Pulmonary hypertension")));
        assertThat(conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(sarahId).stream()
                .filter(row -> row.isActive() && row.isPrimaryCondition()).count()).isEqualTo(1);

        mvc.perform(put("/api/clinician/patients/" + sarahId + "/conditions/" + hypertensionId).session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("Hypertension", "HYPERTENSION", true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryCondition").value(true));
        assertThat(patients.findById(sarahId).orElseThrow().getMedicalCondition()).isEqualTo("Hypertension");
        assertThat(conditions.findById(primaryId).orElseThrow().isPrimaryCondition()).isFalse();
        assertThat(conditions.findById(primaryId).orElseThrow().isActive()).isTrue();

        mvc.perform(put("/api/clinician/patients/" + sarahId + "/conditions/" + diabetesId).session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("Type 2 diabetes", "DIABETES", false, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(put("/api/clinician/patients/" + sarahId + "/conditions/" + hypertensionId).session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("Hypertension", "HYPERTENSION", false, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Set another active condition as primary before deactivating this one."));
        assertThat(conditions.findById(hypertensionId).orElseThrow().isActive()).isTrue();
        assertThat(patients.findById(sarahId).orElseThrow().getMedicalCondition()).isEqualTo("Hypertension");

        long johnConditionId = conditionId(johnId, "Hypertension");
        mvc.perform(put("/api/clinician/patients/" + johnId + "/conditions/" + johnConditionId).session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("Hypertension", "HYPERTENSION", false, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Keep at least one active condition."));
        assertThat(conditions.findById(johnConditionId).orElseThrow().isActive()).isTrue();

        mvc.perform(get("/api/me/conditions").session(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.primary == true)].conditionName", hasItem("Hypertension")))
                .andExpect(jsonPath("$[?(@.conditionName == 'Type 2 diabetes')]").isEmpty())
                .andExpect(jsonPath("$[?(@.conditionName == 'Open-heart surgery recovery')].primary", hasItem(false)));
        mvc.perform(get("/api/me/conditions").session(john))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[?(@.conditionName == 'Open-heart surgery recovery')]").isEmpty())
                .andExpect(jsonPath("$[?(@.conditionName == 'Type 2 diabetes')]").isEmpty())
                .andExpect(jsonPath("$[0].conditionName").value("Hypertension"));

        mvc.perform(get("/api/me/monitoring-plan").session(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("General Daily Wellness"));
        mvc.perform(get("/api/me/check-in/today").session(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.monitoringPlanName").value("General Daily Wellness"));
    }

    private long conditionId(long patientId, String name) {
        return conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId).stream()
                .filter(row -> name.equals(row.getConditionName()))
                .findFirst().orElseThrow().getId();
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

    private void register(String username, String name, String condition) throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(username, name, condition, "America/New_York")))
                .andExpect(status().isCreated());
    }

    private static String registration(String username, String name, String condition, String timezone) {
        return """
                {"username":"%s","password":"%s","fullName":"%s","medicalCondition":"%s","timezone":"%s"}
                """.formatted(username, PASSWORD, name, condition, timezone);
    }
}
