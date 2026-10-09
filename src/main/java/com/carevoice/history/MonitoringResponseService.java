package com.carevoice.history;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.domain.InputMode;
import com.carevoice.observability.VoiceTiming;
import com.carevoice.service.ClinicalMonitoringAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MonitoringResponseService {
    private static final Logger log = LoggerFactory.getLogger(MonitoringResponseService.class);

    private final MonitoringHistoryRecorder recorder;
    private final ClinicalMonitoringAgent agent;

    public MonitoringResponseService(MonitoringHistoryRecorder recorder, ClinicalMonitoringAgent agent) {
        this.recorder = recorder;
        this.agent = agent;
    }

    public ClinicalAgentResponse acceptText(Long sessionId, String message) {
        long started = System.nanoTime();
        try {
            return accept(sessionId, message, InputMode.TEXT);
        } finally {
            VoiceTiming.log(log, "totalMs=" + VoiceTiming.millisSince(started) + " sessionId=" + sessionId);
        }
    }

    public ClinicalAgentResponse acceptTranscript(Long sessionId, String transcript) {
        return accept(sessionId, transcript, InputMode.VOICE);
    }

    private ClinicalAgentResponse accept(Long sessionId, String patientResponse, InputMode inputMode) {
        RecordedTurn recorded = recorder.record(sessionId, patientResponse, inputMode);
        return agent.present(sessionId, recorded.question(), patientResponse, recorded.response());
    }
}
