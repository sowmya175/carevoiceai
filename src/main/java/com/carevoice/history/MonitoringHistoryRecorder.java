package com.carevoice.history;

import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.InputMode;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.MonitoringTurn;
import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.service.AgentTurn;
import com.carevoice.service.ClinicalMonitoringAgent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringHistoryRecorder {
    private final MonitoringSessionRepository sessions;
    private final MonitoringTurnRepository turns;
    private final ClinicalNoteRepository notes;
    private final ClinicalMonitoringAgent agent;

    public MonitoringHistoryRecorder(
            MonitoringSessionRepository sessions,
            MonitoringTurnRepository turns,
            ClinicalNoteRepository notes,
            ClinicalMonitoringAgent agent) {
        this.sessions = sessions;
        this.turns = turns;
        this.notes = notes;
        this.agent = agent;
    }

    @Transactional
    public RecordedTurn record(Long sessionId, String patientResponse, InputMode inputMode) {
        MonitoringSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring session not found: " + sessionId));
        String question = session.getNextQuestion();
        int sequenceNumber = turns.maxSequence(sessionId) + 1;
        AgentTurn agentTurn = agent.processTurn(sessionId, patientResponse);

        MonitoringTurn turn = turns.save(new MonitoringTurn(
                session.getPatient(),
                session,
                sequenceNumber,
                question,
                patientResponse,
                inputMode));
        ClinicalNote note = notes.save(new ClinicalNote(
                turn,
                ExtractedFactsJson.write(agentTurn.extractedFacts()),
                agentTurn.response().riskLevel(),
                session.getEscalationReason()));
        return new RecordedTurn(
                note.getId(),
                sessionId,
                question,
                patientResponse,
                inputMode,
                agentTurn.extractedFacts(),
                agentTurn.response());
    }
}
