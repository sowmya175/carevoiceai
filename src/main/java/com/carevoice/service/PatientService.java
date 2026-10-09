package com.carevoice.service;

import com.carevoice.domain.Patient;
import com.carevoice.dto.patient.PatientResponse;
import com.carevoice.mapper.PatientMapper;
import com.carevoice.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {
    private final PatientRepository patients;

    public PatientService(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional
    public PatientResponse create(String displayName, String monitoringPlan) {
        return PatientMapper.toResponse(patients.save(new Patient(displayName, monitoringPlan)));
    }
}
