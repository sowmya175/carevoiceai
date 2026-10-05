package com.carevoice.history;

import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.MonitoringTurn;
import com.carevoice.domain.SessionStatus;
import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonitoringHistoryQuery {
    private final MonitoringSessionRepository sessions;
    private final MonitoringTurnRepository turns;
    private final ClinicalNoteRepository notes;
    private final PatientRepository patients;

    public MonitoringHistoryQuery(
            MonitoringSessionRepository sessions,
            MonitoringTurnRepository turns,
            ClinicalNoteRepository notes,
            PatientRepository patients) {
        this.sessions = sessions;
        this.turns = turns;
        this.notes = notes;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public SessionHistoryResponse sessionHistory(Long sessionId) {
        MonitoringSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring session not found: " + sessionId));
        Map<Long, ClinicalNote> notesByTurn = new HashMap<>();
        for (ClinicalNote note : notes.findByMonitoringTurn_MonitoringSession_Id(sessionId)) {
            notesByTurn.put(note.getMonitoringTurn().getId(), note);
        }
        List<SessionHistoryResponse.Turn> history = turns.findByMonitoringSession_IdOrderBySequenceNumberAsc(sessionId)
                .stream()
                .map(turn -> toTurn(turn, notesByTurn.get(turn.getId())))
                .toList();
        return new SessionHistoryResponse(
                session.getId(),
                session.getPatient().getId(),
                session.getCreatedAt(),
                session.getStatus(),
                session.getNextQuestion(),
                session.getStatus() != SessionStatus.IN_PROGRESS,
                history);
    }

    @Transactional(readOnly = true)
    public PatientHistoryResponse patientHistory(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new IllegalArgumentException("Patient not found: " + patientId);
        }
        Map<Long, Long> turnCounts = new HashMap<>();
        for (Object[] row : turns.countByPatient(patientId)) {
            turnCounts.put((Long) row[0], (Long) row[1]);
        }
        List<PatientHistoryResponse.SessionSummary> summaries = sessions
                .findByPatient_IdOrderByCreatedAtDescIdDesc(patientId)
                .stream()
                .map(session -> new PatientHistoryResponse.SessionSummary(
                        session.getId(),
                        session.getCreatedAt(),
                        session.getStatus(),
                        session.getRiskLevel(),
                        turnCounts.getOrDefault(session.getId(), 0L)))
                .toList();
        return new PatientHistoryResponse(patientId, summaries);
    }

    private static SessionHistoryResponse.Turn toTurn(MonitoringTurn turn, ClinicalNote note) {
        return new SessionHistoryResponse.Turn(
                turn.getSequenceNumber(),
                turn.getCreatedAt(),
                turn.getQuestion(),
                turn.getPatientResponse(),
                turn.getInputMode(),
                note == null ? null : note.getNoteText(),
                note == null ? Map.of() : ExtractedFactsJson.read(note.getExtractedFactsJson()),
                note == null ? turn.getMonitoringSession().getRiskLevel() : note.getRiskLevel());
    }
}
