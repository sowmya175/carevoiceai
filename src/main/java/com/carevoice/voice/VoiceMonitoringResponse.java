package com.carevoice.voice;

import com.carevoice.agent.ClinicalAgentResponse;

public record VoiceMonitoringResponse(String transcript, ClinicalAgentResponse agentResponse) {}
