package com.carevoice.api;

import com.carevoice.condition.PatientConditionService;
import com.carevoice.condition.PatientConditionService.ClinicianCondition;
import com.carevoice.domain.MonitoringCategory;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clinician/patients/{patientId}/conditions")
public class ClinicianConditionController {
    private final PatientConditionService conditions;

    public ClinicianConditionController(PatientConditionService conditions) {
        this.conditions = conditions;
    }

    @GetMapping
    @PreAuthorize("hasRole('CLINICIAN')")
    public List<ClinicianCondition> list(@PathVariable Long patientId) {
        return conditions.listForClinician(patientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLINICIAN')")
    public ClinicianCondition create(@PathVariable Long patientId, @RequestBody CreateCondition request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a condition or procedure.");
        }
        return conditions.create(patientId, request.conditionName(), request.monitoringCategory(),
                Boolean.TRUE.equals(request.primaryCondition()));
    }

    @PutMapping("/{conditionId}")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ClinicianCondition update(
            @PathVariable Long patientId,
            @PathVariable Long conditionId,
            @RequestBody UpdateCondition request) {
        if (request == null || request.active() == null || request.primaryCondition() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a condition or procedure.");
        }
        return conditions.update(patientId, conditionId, request.conditionName(), request.monitoringCategory(),
                request.active(), request.primaryCondition());
    }

    public record CreateCondition(String conditionName, MonitoringCategory monitoringCategory, Boolean primaryCondition) {}

    public record UpdateCondition(
            String conditionName,
            MonitoringCategory monitoringCategory,
            Boolean active,
            Boolean primaryCondition
    ) {}
}
