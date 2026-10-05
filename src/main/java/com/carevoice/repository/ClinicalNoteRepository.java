package com.carevoice.repository;

import com.carevoice.domain.ClinicalNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, Long> {
    Optional<ClinicalNote> findByMonitoringTurnId(Long monitoringTurnId);

    List<ClinicalNote> findByMonitoringTurn_MonitoringSession_Id(Long sessionId);
}
