package com.carevoice.training;

import com.carevoice.service.MonitoringFieldCatalogService;
import com.carevoice.service.MonitoringFieldCatalogService.CatalogField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-training;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class PlanCatalogAlignmentTest {
    @Autowired MonitoringFieldCatalogService catalog;

    @Test
    void trainingCatalogMatchesPlanAskableRuntimeFields() {
        Set<String> runtime = catalog.list(null, null, true).stream()
                .map(CatalogField::code)
                .collect(Collectors.toSet());
        assertThat(runtime).containsExactlyInAnyOrderElementsOf(PlanTrainingCatalog.planAskableCodes());
        assertThat(runtime).doesNotContain("DIZZINESS", "SHORTNESS_OF_BREATH", "INCISION_STATUS",
                "SWELLING", "MOBILITY", "ACTIVITY_TOLERANCE");
        for (CatalogField field : catalog.list(null, null, true)) {
            var training = PlanTrainingCatalog.definition(field.code());
            assertThat(training.displayName()).isEqualTo(field.displayName());
            assertThat(training.description()).isEqualTo(field.description());
            assertThat(training.answerType()).isEqualTo(field.answerType());
            assertThat(training.minimumValue()).isEqualTo(field.minimumValue());
            assertThat(training.maximumValue()).isEqualTo(field.maximumValue());
            assertThat(training.allowedValues()).containsExactlyInAnyOrderElementsOf(
                    field.options().stream().map(option -> option.code()).toList());
            assertThat(training.categories()).containsExactlyInAnyOrderElementsOf(field.categories());
        }
    }
}
