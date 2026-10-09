package com.carevoice.repository;

import com.carevoice.domain.DailySessionRow;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.SessionStatus;
import com.carevoice.domain.SessionFactsSnapshot;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonitoringSessionRepository extends JpaRepository<MonitoringSession, Long> {
    boolean existsByIdAndPatient_Id(Long id, Long patientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonitoringSession s where s.id = :id")
    Optional<MonitoringSession> findByIdForUpdate(@Param("id") Long id);

    List<MonitoringSession> findByPatient_IdOrderByCreatedAtDescIdDesc(Long patientId);

    Optional<MonitoringSession> findByPatient_IdAndCheckInDate(Long patientId, LocalDate checkInDate);

    Optional<MonitoringSession> findFirstByPatient_IdAndStatusAndCheckInDateLessThanOrderByCheckInDateAscIdAsc(
            Long patientId, SessionStatus status, LocalDate checkInDate);

    @Query("""
            select new com.carevoice.domain.DailySessionRow(
                s.patient.id, s.id, s.checkInDate, s.status, s.monitoringPlanName, s.createdAt, s.completedAt, s.riskLevel)
            from MonitoringSession s
            where s.patient.id in :patientIds
              and s.checkInDate is not null
              and (s.status = :inProgress or s.checkInDate in :dates)
            """)
    List<DailySessionRow> findForDailyStatus(
            @Param("patientIds") Collection<Long> patientIds,
            @Param("inProgress") SessionStatus inProgress,
            @Param("dates") Collection<LocalDate> dates);

    @Query("""
            select new com.carevoice.domain.SessionFactsSnapshot(
                s.id, s.createdAt, s.status, s.riskLevel, s.painScore, s.sleepQuality,
                s.appetite, s.medicationTaken, s.dizziness, s.shortnessOfBreath,
                s.lossOfConsciousness, s.dizzinessOnset, s.temperature, s.checkInDate, s.monitoringPlanName)
            from MonitoringSession s
            where s.patient.id = :patientId and s.status in :statuses
            order by s.createdAt desc, s.id desc
            """)
    List<SessionFactsSnapshot> findRecentFacts(
            @Param("patientId") Long patientId,
            @Param("statuses") List<SessionStatus> statuses,
            Pageable pageable);
}
