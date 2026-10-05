package com.carevoice.history;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.domain.InputMode;
import org.springframework.stereotype.Service;

@Service
public class MonitoringResponseService {
    private final MonitoringHistoryRecorder recorder;
    private final ClinicalNoteAttacher notes;

    public MonitoringResponseService(MonitoringHistoryRecorder recorder, ClinicalNoteAttacher notes) {
        this.recorder = recorder;
        this.notes = notes;
    }

    public ClinicalAgentResponse acceptText(Long sessionId, String message) {
        return accept(sessionId, message, InputMode.TEXT);
    }

    public ClinicalAgentResponse acceptTranscript(Long sessionId, String transcript) {
        return accept(sessionId, transcript, InputMode.VOICE);
    }

    private ClinicalAgentResponse accept(Long sessionId, String patientResponse, InputMode inputMode) {
        RecordedTurn recorded = recorder.record(sessionId, patientResponse, inputMode);
        notes.attach(recorded);
        return recorded.response();
    }
}
