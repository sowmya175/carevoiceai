package com.carevoice.dto.voice;

import com.carevoice.dto.monitoring.ClinicalAgentResponse;

public record VoiceMonitoringResponse(String transcript, ClinicalAgentResponse agentResponse) {}
