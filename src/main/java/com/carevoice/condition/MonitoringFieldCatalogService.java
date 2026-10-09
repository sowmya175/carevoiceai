package com.carevoice.condition;

import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringFieldDefinition;
import com.carevoice.domain.MonitoringFieldOption;
import com.carevoice.domain.MonitoringFieldRuntimeSupport;
import com.carevoice.repository.MonitoringFieldDefinitionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringFieldCatalogService {
    private final MonitoringFieldDefinitionRepository definitions;

    public MonitoringFieldCatalogService(MonitoringFieldDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @Transactional(readOnly = true)
    public MonitoringFieldDefinition getDefinitionForLegacyField(MonitoringField field) {
        return definitions.findByLegacyField(field)
                .orElseThrow(() -> new IllegalStateException("Monitoring field is not in the catalog."));
    }

    @Transactional(readOnly = true)
    public Optional<MonitoringFieldDefinition> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return definitions.findByCode(code.trim());
    }

    @Transactional(readOnly = true)
    public List<MonitoringFieldDefinition> getSupportedFields() {
        return load().stream()
                .filter(field -> field.isActive() && field.getRuntimeSupport() == MonitoringFieldRuntimeSupport.SUPPORTED)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonitoringFieldDefinition> getFieldsByCategory(MonitoringCategory category) {
        if (category == null) {
            return List.of();
        }
        return load().stream()
                .filter(field -> field.getCategories().contains(category))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean validateRuntimeSupported(String code) {
        return findByCode(code)
                .filter(field -> field.isActive() && field.getRuntimeSupport() == MonitoringFieldRuntimeSupport.SUPPORTED)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<MonitoringFieldDefinition> all() {
        return load();
    }

    /**
     * A field is plan-askable when the daily check-in can ask it, extract it, store it, and display it.
     * Extractable fields without a {@link MonitoringField} entry, and catalog-only fields, are excluded.
     */
    public static boolean planAskable(MonitoringFieldDefinition field) {
        return field.isActive()
                && field.getRuntimeSupport() == MonitoringFieldRuntimeSupport.SUPPORTED
                && field.getLegacyField() != null;
    }

    @Transactional(readOnly = true)
    public List<CatalogField> list(MonitoringFieldRuntimeSupport runtimeSupport, MonitoringCategory category) {
        return list(runtimeSupport, category, null);
    }

    @Transactional(readOnly = true)
    public List<CatalogField> list(
            MonitoringFieldRuntimeSupport runtimeSupport, MonitoringCategory category, Boolean planAskable) {
        return load().stream()
                .filter(field -> runtimeSupport == null || field.getRuntimeSupport() == runtimeSupport)
                .filter(field -> category == null || field.getCategories().contains(category))
                .filter(field -> planAskable == null || planAskable(field) == planAskable)
                .map(CatalogField::from)
                .toList();
    }

    private List<MonitoringFieldDefinition> load() {
        return definitions.findAll().stream()
                .sorted(Comparator.comparing(MonitoringFieldDefinition::getCode))
                .toList();
    }

    public record CatalogField(
            String code,
            String displayName,
            String description,
            com.carevoice.domain.MonitoringAnswerType answerType,
            String unit,
            Double minimumValue,
            Double maximumValue,
            List<CatalogOption> options,
            MonitoringFieldRuntimeSupport runtimeSupport,
            Set<MonitoringCategory> categories,
            boolean planAskable
    ) {
        static CatalogField from(MonitoringFieldDefinition field) {
            List<CatalogOption> options = field.getOptions().stream()
                    .filter(MonitoringFieldOption::isActive)
                    .sorted(Comparator.comparingInt(MonitoringFieldOption::getDisplayOrder)
                            .thenComparing(MonitoringFieldOption::getCode))
                    .map(option -> new CatalogOption(option.getCode(), option.getDisplayLabel(), option.getDisplayOrder()))
                    .toList();
            return new CatalogField(
                    field.getCode(), field.getDisplayName(), field.getDescription(), field.getAnswerType(),
                    field.getUnit(), field.getMinimumValue(), field.getMaximumValue(), options,
                    field.getRuntimeSupport(), Set.copyOf(field.getCategories()),
                    MonitoringFieldCatalogService.planAskable(field));
        }
    }

    public record CatalogOption(String code, String displayLabel, int displayOrder) {}
}
