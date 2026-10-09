package com.carevoice.condition;

import com.carevoice.auth.AccountRole;
import com.carevoice.auth.UserAccount;
import com.carevoice.auth.UserAccountRepository;
import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringFieldRuntimeSupport;
import com.carevoice.repository.MonitoringFieldDefinitionRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:field-catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class MonitoringFieldCatalogTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired WebApplicationContext context;
    @Autowired MonitoringFieldCatalogService catalog;
    @Autowired MonitoringFieldCatalogSeeder seeder;
    @Autowired MonitoringFieldDefinitionRepository definitions;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void everyLegacyFieldMapsOnceAndCatalogOnlyFieldsStayInactive() throws Exception {
        long before = definitions.count();
        seeder.run(null);
        assertThat(definitions.count()).isEqualTo(before);
        assertThat(definitions.findAll()).extracting(definition -> definition.getCode()).doesNotHaveDuplicates();

        for (MonitoringField field : MonitoringField.values()) {
            var definition = catalog.getDefinitionForLegacyField(field);
            assertThat(definition.getCode()).isEqualTo(field.name());
            assertThat(definition.getLegacyField()).isEqualTo(field);
            assertThat(catalog.getDefinitionForLegacyField(field).getCode()).isEqualTo(definition.getCode());
            assertThat(definition.getRuntimeSupport()).isEqualTo(MonitoringFieldRuntimeSupport.SUPPORTED);
        }
        assertThat(catalog.findByCode("NOT_A_FIELD")).isEmpty();
        assertThat(catalog.validateRuntimeSupported("NOT_A_FIELD")).isFalse();
        assertThat(catalog.validateRuntimeSupported("PAIN_SCORE")).isTrue();
        assertThat(catalog.validateRuntimeSupported("INCISION_STATUS")).isFalse();

        assertThat(catalog.getSupportedFields())
                .allMatch(field -> field.getRuntimeSupport() == MonitoringFieldRuntimeSupport.SUPPORTED)
                .extracting(field -> field.getCode())
                .contains("PAIN_SCORE", "MEDICATION_TAKEN", "SLEEP_QUALITY", "DIZZINESS", "SHORTNESS_OF_BREATH")
                .doesNotContain("INCISION_STATUS", "SWELLING", "MOBILITY", "ACTIVITY_TOLERANCE");

        for (String code : List.of("INCISION_STATUS", "SWELLING", "MOBILITY", "ACTIVITY_TOLERANCE")) {
            var field = catalog.findByCode(code).orElseThrow();
            assertThat(field.getRuntimeSupport()).isEqualTo(MonitoringFieldRuntimeSupport.CATALOG_ONLY);
            assertThat(field.getLegacyField()).isNull();
        }
        var incision = catalog.list(null, null).stream()
                .filter(field -> field.code().equals("INCISION_STATUS"))
                .findFirst().orElseThrow();
        assertThat(incision.options()).extracting(option -> option.code())
                .containsExactly("NORMAL", "REDNESS", "SWELLING", "DRAINAGE", "OTHER");
        assertThat(catalog.getFieldsByCategory(MonitoringCategory.POST_OPERATIVE))
                .extracting(field -> field.getCode())
                .contains("PAIN_SCORE", "INCISION_STATUS", "SHORTNESS_OF_BREATH");

        accounts.saveAndFlush(new UserAccount("field-doc", passwords.encode(PASSWORD), AccountRole.CLINICIAN, null));
        mvc.perform(post("/api/auth/register/patient").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"field-pat","password":"%s","fullName":"Pat","medicalCondition":"Recovery","timezone":"UTC"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated());
        MockHttpSession doctor = login("field-doc", "/api/auth/login/clinician");
        MockHttpSession patient = login("field-pat", "/api/auth/login/patient");
        mvc.perform(get("/api/clinician/monitoring-fields")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clinician/monitoring-fields").session(patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/clinician/monitoring-fields").session(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'INCISION_STATUS')].runtimeSupport", hasItem("CATALOG_ONLY")))
                .andExpect(jsonPath("$[?(@.code == 'PAIN_SCORE')].answerType", hasItem("NUMBER")));
        mvc.perform(get("/api/clinician/monitoring-fields").session(doctor).param("runtimeSupport", "SUPPORTED"))
                .andExpect(jsonPath("$[*].code", not(hasItem("INCISION_STATUS"))))
                .andExpect(jsonPath("$[*].code", hasItem("PAIN_SCORE")));
        mvc.perform(get("/api/clinician/monitoring-fields").session(doctor).param("category", "POST_OPERATIVE"))
                .andExpect(jsonPath("$[*].code", hasItem("PAIN_SCORE")))
                .andExpect(jsonPath("$[*].code", hasItem("INCISION_STATUS")));
        mvc.perform(get("/api/clinician/monitoring-fields").session(doctor).param("runtimeSupport", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login(String username, String path) throws Exception {
        MvcResult result = mvc.perform(post(path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
