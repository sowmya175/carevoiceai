package com.carevoice.api;

import com.carevoice.domain.Patient;
import com.carevoice.repository.PatientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientRepository repository;

    public PatientController(PatientRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Patient create(@Valid @RequestBody CreatePatientRequest request) {
        return repository.save(new Patient(request.displayName(), request.monitoringPlan()));
    }

    public record CreatePatientRequest(
            @NotBlank String displayName,
            @NotBlank String monitoringPlan
    ) {}
}
