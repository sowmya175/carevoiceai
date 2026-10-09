package com.carevoice.repository;

import com.carevoice.domain.MonitoringPlanProposal;
import com.carevoice.domain.ProposalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonitoringPlanProposalRepository extends JpaRepository<MonitoringPlanProposal, Long> {
    List<MonitoringPlanProposal> findByPatient_IdOrderByCreatedAtDesc(Long patientId);

    List<MonitoringPlanProposal> findByPatient_IdAndStatus(Long patientId, ProposalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MonitoringPlanProposal p where p.id = :id")
    Optional<MonitoringPlanProposal> lockById(@Param("id") Long id);
}
