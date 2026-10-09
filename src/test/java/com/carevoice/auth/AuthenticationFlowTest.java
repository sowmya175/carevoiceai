package com.carevoice.auth;

import com.carevoice.domain.Patient;
import com.carevoice.repository.PatientRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=true",
        "carevoice.voice.provider=groq",
        "carevoice.voice.groq.api-key=test-not-a-real-key"
})
class AuthenticationFlowTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired PatientRepository patients;
    @Autowired PasswordEncoder passwords;
    @Autowired Environment environment;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registrationHashesThePasswordAndRejectsDuplicatesAndInvalidTimezones() throws Exception {
        Patient legacy = patients.save(new Patient("Existing Patient", "Legacy plan"));
        long before = patients.count();

        MvcResult created = mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("ada01", "Ada Lovelace", "America/New_York")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.username").value("ada01"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.patient.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.patient.medicalCondition").value("Asthma"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD);
        Number patientId = JsonPath.read(body, "$.patient.id");
        assertThat(patientId.longValue()).isNotEqualTo(legacy.getId());

        UserAccount saved = accounts.findByUsername("ada01").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getRole()).isEqualTo(AccountRole.PATIENT);
        assertThat(saved.getPatient().getId()).isEqualTo(patientId.longValue());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(body).doesNotContain(saved.getPasswordHash());

        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Ada01", "Someone Else", "America/Chicago")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("That username is already taken."));
        assertThat(patients.count()).isEqualTo(before + 1);

        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("sarah01", "Sarah Miller", "America/New_York")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Sarah01", "Sarah Again", "America/New_York")))
                .andExpect(status().isConflict());
        assertThat(patients.count()).isEqualTo(before + 2);

        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s","fullName":"No Name","medicalCondition":"Asthma","timezone":"UTC","role":"CLINICIAN"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"rolecheck","password":"%s","fullName":"Role Check","medicalCondition":"Asthma","timezone":"UTC","role":"CLINICIAN"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"));
        assertThat(accounts.findByUsername("rolecheck").orElseThrow().getRole()).isEqualTo(AccountRole.PATIENT);

        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("other01", "Other Person", "Not/AZone")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Choose a valid IANA timezone."));
        assertThat(patients.count()).isEqualTo(before + 3);
        assertThat(patients.findById(legacy.getId()).orElseThrow().getDisplayName()).isEqualTo("Existing Patient");
        assertThat(accounts.existsByPatient_Id(legacy.getId())).isFalse();
    }

    @Test
    void loginMeAndLogoutUseTheServerSession() throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("bea01", "Bea Patient", "UTC")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required."));

        String unknown = mvc.perform(post("/api/auth/login/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("missing01")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrong = mvc.perform(post("/api/auth/login/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("bea01", "wrong-password-value")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(unknown).isEqualTo(wrong);
        assertThat(unknown).contains("Invalid username or password.");
        assertThat(unknown).doesNotContain("missing01").doesNotContain("not found");

        MvcResult login = mvc.perform(post("/api/auth/login/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("bea01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("bea01"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.patient.fullName").value("Bea Patient"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(contentDoesNotContainPassword())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.patient.timezone").value("UTC"));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotReadOrWriteAnotherPatientsMonitoringData() throws Exception {
        mvc.perform(post("/api/auth/register/patient").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registration("patienta", "Patient A", "America/New_York"))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register/patient").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registration("patientb", "Patient B", "Europe/London"))).andExpect(status().isCreated());
        MockHttpSession sessionA = loginSession("patienta");
        MockHttpSession sessionB = loginSession("patientb");

        MvcResult started = mvc.perform(post("/api/monitoring/me/sessions").session(sessionA).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andReturn();
        Number sessionId = JsonPath.read(started.getResponse().getContentAsString(), "$.sessionId");
        Number patientA = JsonPath.read(
                mvc.perform(get("/api/auth/me").session(sessionA)).andReturn().getResponse().getContentAsString(),
                "$.patient.id");
        Number patientB = JsonPath.read(
                mvc.perform(get("/api/auth/me").session(sessionB)).andReturn().getResponse().getContentAsString(),
                "$.patient.id");

        mvc.perform(get("/api/monitoring/sessions/" + sessionId + "/history").session(sessionB))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/" + patientA + "/history").session(sessionB))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/" + patientA + "/longitudinal-summary").session(sessionB))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/monitoring/sessions/" + sessionId + "/messages").session(sessionB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"I feel dizzy.\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/monitoring/sessions/" + sessionId + "/voice").file(audio()).session(sessionB).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/monitoring/sessions/" + sessionId + "/voice/transcribe").file(audio()).session(sessionB).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/monitoring/patients/" + patientA + "/sessions").session(sessionB).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/monitoring/sessions/" + sessionId + "/history").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientA.longValue()));
        mvc.perform(get("/api/patients/" + patientA + "/history").session(sessionA)).andExpect(status().isOk());
        mvc.perform(get("/api/patients/" + patientA + "/longitudinal-summary").session(sessionA)).andExpect(status().isOk());
        mvc.perform(post("/api/monitoring/sessions/" + sessionId + "/messages").session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"I feel okay today.\"}"))
                .andExpect(status().isOk());

        accounts.saveAndFlush(new UserAccount("doctor1", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        MockHttpSession doctor = loginSession("doctor1", "/api/auth/login/clinician");
        mvc.perform(post("/api/auth/login/patient").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(login("doctor1")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login/clinician").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(login("patienta")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clinician/patients").session(doctor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fullName == 'Patient A')]").exists())
                .andExpect(jsonPath("$[?(@.fullName == 'Patient B')]").exists())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/patients/" + patientA + "/history").session(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/patients/" + patientB + "/history").session(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/patients/" + patientA + "/longitudinal-summary").session(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/patients/" + patientB + "/longitudinal-summary").session(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/monitoring/sessions/" + sessionId + "/history").session(doctor)).andExpect(status().isOk());
        mvc.perform(post("/api/monitoring/sessions/" + sessionId + "/messages").session(doctor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Clinician note.\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/monitoring/sessions/" + sessionId + "/voice").file(audio()).session(doctor).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/monitoring/sessions/" + sessionId + "/voice/transcribe").file(audio()).session(doctor).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/patients").session(sessionA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/patients")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/monitoring/sessions/" + sessionId + "/history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/patients").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Claimed\",\"monitoringPlan\":\"Daily monitoring\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/patients").session(sessionA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Claimed\",\"monitoringPlan\":\"Daily monitoring\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionCookieIsHttpOnlyAndCorsAllowsOnlyTheConfiguredOrigin() throws Exception {
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(environment.getProperty("server.servlet.session.tracking-modes")).contains("cookie");

        mvc.perform(options("/api/auth/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Origin", not(containsString("*"))));
        mvc.perform(options("/api/clinician/patients/1/monitoring-plan")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("X-CSRF-TOKEN")));

        mvc.perform(options("/api/auth/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private MockHttpSession loginSession(String username) throws Exception {
        return loginSession(username, "/api/auth/login/patient");
    }

    private MockHttpSession loginSession(String username, String path) throws Exception {
        MvcResult result = mvc.perform(post(path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(login(username)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile("audio", "answer.webm", "audio/webm", new byte[] {1, 2, 3, 4});
    }

    private static org.springframework.test.web.servlet.ResultMatcher contentDoesNotContainPassword() {
        return result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD);
    }

    private static String registration(String username, String name, String timezone) {
        return """
                {"username":"%s","password":"%s","fullName":"%s","medicalCondition":"Asthma","timezone":"%s"}
                """.formatted(username, PASSWORD, name, timezone);
    }

    private static String login(String username) {
        return login(username, PASSWORD);
    }

    private static String login(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }
}
