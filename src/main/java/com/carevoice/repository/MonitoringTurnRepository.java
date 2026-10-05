package com.carevoice.repository;

import com.carevoice.domain.MonitoringTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MonitoringTurnRepository extends JpaRepository<MonitoringTurn, Long> {
    @Query("select coalesce(max(t.sequenceNumber), 0) from MonitoringTurn t where t.monitoringSession.id = :sessionId")
    int maxSequence(@Param("sessionId") Long sessionId);

    List<MonitoringTurn> findByMonitoringSession_IdOrderBySequenceNumberAsc(Long sessionId);

    @Query("""
            select t.monitoringSession.id, count(t)
            from MonitoringTurn t
            where t.patient.id = :patientId
            group by t.monitoringSession.id
            """)
    List<Object[]> countByPatient(@Param("patientId") Long patientId);
}
