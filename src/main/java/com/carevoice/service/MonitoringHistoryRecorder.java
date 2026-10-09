package com.carevoice.service;

import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.InputMode;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.MonitoringTurn;

import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MonitoringHistoryRecorder {
    private static final Logger log = LoggerFactory.getLogger(MonitoringHistoryRecorder.class);

    private final MonitoringSessionRepository sessions;
    private final MonitoringTurnRepository turns;
    private final ClinicalNoteRepository notes;
    private final ClinicalMonitoringAgent agent;
    private final ApplicationEventPublisher events;
    private final ClinicalNoteAttacher noteAttacher;

    public MonitoringHistoryRecorder(
            MonitoringSessionRepository sessions,
            MonitoringTurnRepository turns,
            ClinicalNoteRepository notes,
            ClinicalMonitoringAgent agent,
            ApplicationEventPublisher events,
            ClinicalNoteAttacher noteAttacher) {
        this.sessions = sessions;
        this.turns = turns;
        this.notes = notes;
        this.agent = agent;
        this.events = events;
        this.noteAttacher = noteAttacher;
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
        RecordedTurn recorded = new RecordedTurn(
                note.getId(),
                sessionId,
                question,
                patientResponse,
                inputMode,
                agentTurn.extractedFacts(),
                agentTurn.response());
        scheduleNote(recorded);
        return recorded;
    }

    private void scheduleNote(RecordedTurn recorded) {
        long started = System.nanoTime();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            events.publishEvent(new ClinicalNoteRequested(
                    recorded.noteId(),
                    recorded.sessionId(),
                    recorded.question(),
                    recorded.patientResponse(),
                    recorded.extractedFacts()));
        } else {
            noteAttacher.attach(recorded);
        }
        VoiceTiming.log(log, "noteSchedulingMs=" + VoiceTiming.millisSince(started)
                + " sessionId=" + recorded.sessionId());
    }
}
