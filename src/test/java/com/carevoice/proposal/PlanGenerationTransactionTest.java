package com.carevoice.proposal;
import com.carevoice.service.ActivePlanGenerationModel;
import com.carevoice.service.GenerationMetadata;
import com.carevoice.service.MonitoringPlanProposalService;
import com.carevoice.domain.Patient;
import com.carevoice.exception.PlanGenerationFailedException;
import com.carevoice.service.PlanGenerationTrace;
import com.carevoice.mapper.ProposalViews;

import com.carevoice.domain.AccountRole;
import com.carevoice.domain.UserAccount;
import com.carevoice.repository.UserAccountRepository;
import com.carevoice.service.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.service.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.service.PlanTaskContract;
import com.carevoice.repository.MonitoringPlanProposalRepository;
import com.carevoice.repository.PatientConditionRepository;
import com.carevoice.repository.PatientRepository;
import com.carevoice.training.PlanExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-generation-tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.plan-ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class PlanGenerationTransactionTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired WebApplicationContext context;
    @Autowired MonitoringPlanProposalService proposals;
    @Autowired MonitoringPlanProposalRepository proposalRows;
    @Autowired PatientRepository patients;
    @Autowired PatientConditionRepository conditions;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired PlanGenerationTrace trace;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ActivePlanGenerationModel model;
    MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        if (accounts.findByUsername("plan-tx-doc").isEmpty()) {
            accounts.saveAndFlush(new UserAccount("plan-tx-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        }
    }

    @Test
    void modelCallStaysOutsideThePersistenceTransactionAndKeepsTheReadSnapshot() throws Exception {
        long patientId = register("plan-tx-patient", "Snapshot Patient");
        var doctor = login("plan-tx-doc");
        long conditionId = conditionId(doctor, patientId, "Snapshot Patient condition");
        mvc.perform(put("/api/clinician/patients/" + patientId + "/conditions/" + conditionId).session(doctor).with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"conditionName":"Hypertension","monitoringCategory":"HYPERTENSION","active":true,"primaryCondition":true}
                                """))
                .andExpect(status().isOk());
        AtomicBoolean outside = new AtomicBoolean(false);
        when(model.generate(any())).thenAnswer(invocation -> {
            outside.set(!TransactionSynchronizationManager.isActualTransactionActive());
            PatientPlanGenerationContext generation = invocation.getArgument(0);
            new TransactionTemplate(transactions).executeWithoutResult(status ->
                    conditions.findById(generation.primaryCondition().patientConditionId()).orElseThrow()
                            .rename("Changed during generation"));
            trace.record(new GenerationMetadata(
                    "vertex-tuned",
                    "projects/demo/locations/us-central1/models/carevoice-plan",
                    PlanExample.DATASET_VERSION,
                    PlanTaskContract.VERSION));
            return new PlanGenerationResult(List.of("HYPERTENSION"), List.of(new GeneratedQuestion(
                    "MEDICATION_TAKEN", "Have you taken your prescribed medications today?", true,
                    List.of(generation.primaryCondition().patientConditionId()), "daily")));
        });

        var account = accounts.findByUsername("plan-tx-doc").orElseThrow();
        var view = proposals.generate(patientId, account);
        assertThat(outside).isTrue();
        assertThat(view.conditions()).singleElement().extracting("conditionName").isEqualTo("Hypertension");
        assertThat(view.warnings()).contains(ProposalViews.CONDITIONS_CHANGED);
        var saved = proposalRows.findById(view.id()).orElseThrow();
        assertThat(saved.getGenerationProvider()).isEqualTo("vertex-tuned");
        assertThat(saved.getGenerationModel()).contains("carevoice-plan");
        assertThat(saved.getDatasetVersion()).isEqualTo(PlanExample.DATASET_VERSION);
        assertThat(saved.getTaskContractVersion()).isEqualTo(PlanTaskContract.VERSION);
        mvc.perform(get("/api/clinician/monitoring-plan-proposals/" + view.id()).session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationProvider").doesNotExist())
                .andExpect(jsonPath("$.generationModel").doesNotExist())
                .andExpect(jsonPath("$.conditions[0].conditionName").value("Hypertension"));
        mvc.perform(get("/api/clinician/patients/" + patientId + "/monitoring-plan").session(doctor))
                .andExpect(jsonPath("$.name").value("General Daily Wellness"));
    }

    @Test
    void failedGenerationLeavesTheAssignedPlanUntouched() throws Exception {
        long patientId = register("plan-tx-fail", "Failure Patient");
        var doctor = login("plan-tx-doc");
        when(model.generate(any())).thenThrow(new PlanGenerationFailedException());
        var account = accounts.findByUsername("plan-tx-doc").orElseThrow();
        assertThatThrownBy(() -> proposals.generate(patientId, account))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(proposalRows.findByPatient_IdOrderByCreatedAtDesc(patientId)).isEmpty();
        mvc.perform(get("/api/clinician/patients/" + patientId + "/monitoring-plan").session(doctor))
                .andExpect(jsonPath("$.name").value("General Daily Wellness"));
    }

    private long register(String username, String name) throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","fullName":"%s","medicalCondition":"%s condition","timezone":"America/New_York"}
                                """.formatted(username, PASSWORD, name, name)))
                .andExpect(status().isCreated());
        return patients.findAll().stream().filter(patient -> name.equals(patient.getFullName())).findFirst().orElseThrow().getId();
    }

    private org.springframework.mock.web.MockHttpSession login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login/clinician").with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
    }

    private long conditionId(org.springframework.mock.web.MockHttpSession doctor, long patientId, String name) throws Exception {
        MvcResult result = mvc.perform(get("/api/clinician/patients/" + patientId + "/conditions").session(doctor))
                .andExpect(status().isOk())
                .andReturn();
        Object value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                "$.[?(@.conditionName == '" + name + "')].id");
        if (value instanceof java.util.List<?> list) {
            value = list.getFirst();
        }
        return ((Number) value).longValue();
    }
}
