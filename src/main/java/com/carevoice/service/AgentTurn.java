package com.carevoice.service;

import com.carevoice.dto.monitoring.ClinicalAgentResponse;

public record AgentTurn(ClinicalAgentResponse response, ExtractedClinicalFacts extractedFacts) {}
