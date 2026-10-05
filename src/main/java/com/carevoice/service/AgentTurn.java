package com.carevoice.service;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.agent.ExtractedClinicalFacts;

public record AgentTurn(ClinicalAgentResponse response, ExtractedClinicalFacts extractedFacts) {}
