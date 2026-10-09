package com.carevoice.api;

import com.carevoice.auth.AccountService;
import com.carevoice.plan.MonitoringPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinician")
public class ClinicianMonitoringPlanController {
    private final MonitoringPlanService monitoringPlans;
    private final AccountService accounts;

    public ClinicianMonitoringPlanController(MonitoringPlanService monitoringPlans, AccountService accounts) {
        this.monitoringPlans = monitoringPlans;
        this.accounts = accounts;
    }

    @GetMapping("/monitoring-plans")
    @PreAuthorize("hasRole('CLINICIAN')")
    public List<MonitoringPlanService.PlanSummary> list() {
        return monitoringPlans.availablePlans();
    }

    @GetMapping("/patients/{patientId}/monitoring-plan")
    @PreAuthorize("hasRole('CLINICIAN')")
    public MonitoringPlanService.AssignmentView current(@PathVariable Long patientId) {
        return monitoringPlans.activeAssignment(patientId);
    }

    @PutMapping("/patients/{patientId}/monitoring-plan")
    @PreAuthorize("hasRole('CLINICIAN')")
    public MonitoringPlanService.AssignmentView assign(
            @PathVariable Long patientId,
            @Valid @RequestBody AssignPlanRequest request,
            Authentication authentication) {
        return monitoringPlans.assign(patientId, request.monitoringPlanId(), accounts.current(authentication));
    }

    public record AssignPlanRequest(@NotNull Long monitoringPlanId) {}
}
