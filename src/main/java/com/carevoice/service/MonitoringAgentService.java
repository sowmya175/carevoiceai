package com.carevoice.service;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringAgentService {
    private final PatientRepository patientRepository;
    private final MonitoringSessionRepository sessionRepository;

    public MonitoringAgentService(
            PatientRepository patientRepository,
            MonitoringSessionRepository sessionRepository) {
        this.patientRepository = patientRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public MonitoringSession startSession(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));
        MonitoringSession session = new MonitoringSession(patient);
        session.setNextQuestion("Tell me how you are feeling today in your own words.");
        return sessionRepository.save(session);
    }
}
