package com.carevoice.repository;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonitoringFieldDefinitionRepository extends JpaRepository<MonitoringFieldDefinition, Long> {
    Optional<MonitoringFieldDefinition> findByCode(String code);
    Optional<MonitoringFieldDefinition> findByLegacyField(MonitoringField legacyField);
    boolean existsByCode(String code);
}
