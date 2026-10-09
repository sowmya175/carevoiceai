package com.carevoice.service;

import com.carevoice.domain.MonitoringFieldDefinition;
import com.carevoice.repository.MonitoringFieldDefinitionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Inserts catalog rows once. Existing codes are left unchanged. */
@Component
@Order(6)
public class MonitoringFieldCatalogSeeder implements ApplicationRunner {
    private final MonitoringFieldDefinitionRepository definitions;

    public MonitoringFieldCatalogSeeder(MonitoringFieldDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (MonitoringFieldCatalog.Seed seed : MonitoringFieldCatalog.seeds()) {
            if (definitions.existsByCode(seed.code())) {
                continue;
            }
            MonitoringFieldDefinition definition = new MonitoringFieldDefinition(
                    seed.code(), seed.displayName(), seed.description(), seed.answerType(),
                    seed.unit(), seed.minimumValue(), seed.maximumValue(),
                    seed.runtimeSupport(), seed.legacyField(), seed.categories());
            for (MonitoringFieldCatalog.Option option : seed.options()) {
                definition.addOption(option.code(), option.displayLabel(), option.displayOrder());
            }
            definitions.save(definition);
        }
    }
}
