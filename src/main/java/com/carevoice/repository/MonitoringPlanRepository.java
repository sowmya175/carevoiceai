package com.carevoice.repository;

import com.carevoice.domain.MonitoringPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitoringPlanRepository extends JpaRepository<MonitoringPlan, Long> {
    Optional<MonitoringPlan> findByCode(String code);

    List<MonitoringPlan> findByActiveTrueAndOwnerPatientIdIsNullOrderByNameAsc();
}
