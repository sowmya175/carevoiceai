package com.carevoice.repository;

import com.carevoice.domain.MonitoringPlanQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoringPlanQuestionRepository extends JpaRepository<MonitoringPlanQuestion, Long> {
    List<MonitoringPlanQuestion> findByMonitoringPlan_IdAndActiveTrueOrderByDisplayOrderAsc(Long monitoringPlanId);

    long countByMonitoringPlan_Id(Long monitoringPlanId);
}
