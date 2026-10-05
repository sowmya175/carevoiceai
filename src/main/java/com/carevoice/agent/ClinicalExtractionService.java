package com.carevoice.agent;

/**
 * Turns one patient message into structured monitoring facts.
 * Implementations extract language only. They do not assign risk or session status.
 */
public interface ClinicalExtractionService {

    ExtractedClinicalFacts extract(String message, MonitoringSessionContext context);
}
