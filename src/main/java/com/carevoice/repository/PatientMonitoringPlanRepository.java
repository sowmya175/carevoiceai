package com.carevoice.repository;

import com.carevoice.domain.PatientMonitoringPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PatientMonitoringPlanRepository extends JpaRepository<PatientMonitoringPlan, Long> {
    @EntityGraph(attributePaths = "monitoringPlan")
    List<PatientMonitoringPlan> findByPatient_IdAndActiveTrue(Long patientId);

    List<PatientMonitoringPlan> findByPatient_Id(Long patientId);

    @Query("""
            select a.patient.id, a.monitoringPlan.name
            from PatientMonitoringPlan a
            where a.active = true and a.patient.id in :patientIds
            """)
    List<Object[]> findActivePlanNames(@Param("patientIds") Collection<Long> patientIds);
}
