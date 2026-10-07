package com.carevoice.repository;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.SessionStatus;
import com.carevoice.longitudinal.SessionFactsSnapshot;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonitoringSessionRepository extends JpaRepository<MonitoringSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonitoringSession s where s.id = :id")
    Optional<MonitoringSession> findByIdForUpdate(@Param("id") Long id);

    List<MonitoringSession> findByPatient_IdOrderByCreatedAtDescIdDesc(Long patientId);

    @Query("""
            select new com.carevoice.longitudinal.SessionFactsSnapshot(
                s.id, s.createdAt, s.status, s.riskLevel, s.painScore, s.sleepQuality,
                s.appetite, s.medicationTaken, s.dizziness, s.shortnessOfBreath,
                s.lossOfConsciousness, s.dizzinessOnset, s.temperature)
            from MonitoringSession s
            where s.patient.id = :patientId and s.status in :statuses
            order by s.createdAt desc, s.id desc
            """)
    List<SessionFactsSnapshot> findRecentFacts(
            @Param("patientId") Long patientId,
            @Param("statuses") List<SessionStatus> statuses,
            Pageable pageable);
}
