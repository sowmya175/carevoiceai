package com.carevoice.controller;

import com.carevoice.dto.monitoring.DailyCheckInStatusResponse;
import com.carevoice.dto.monitoring.SessionResponse;
import com.carevoice.mapper.SessionMapper;
import com.carevoice.service.AccountService;
import com.carevoice.service.DailyCheckInService;
import com.carevoice.service.MonitoringAgentService;
import com.carevoice.service.MonitoringPlanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class CurrentPatientController {
    private final AccountService accounts;
    private final MonitoringAgentService sessions;
    private final MonitoringPlanService monitoringPlans;
    private final DailyCheckInService dailyCheckIns;
    public CurrentPatientController(
            AccountService accounts,
            MonitoringAgentService sessions,
            MonitoringPlanService monitoringPlans,
            DailyCheckInService dailyCheckIns) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.monitoringPlans = monitoringPlans;
        this.dailyCheckIns = dailyCheckIns;
    }
    /**
     * Kept for API compatibility. It uses the same daily get-or-create path as
     * {@code POST /api/me/check-in/today}. The patient UI calls the /api/me endpoint.
     */
    @PostMapping("/api/monitoring/me/sessions")
    public SessionResponse start(Authentication authentication) {
        return SessionMapper.toResponse(sessions.startSession(accounts.patientId(authentication)));
    }

    @GetMapping("/api/me/check-in/today")
    @PreAuthorize("hasRole('PATIENT')")
    public DailyCheckInStatusResponse today(Authentication authentication) {
        return dailyCheckIns.status(accounts.patientId(authentication));
    }

    @PostMapping("/api/me/check-in/today")
    @PreAuthorize("hasRole('PATIENT')")
    public SessionResponse startToday(Authentication authentication) {
        return SessionMapper.toResponse(dailyCheckIns.startToday(accounts.patientId(authentication)));
    }

    @GetMapping("/api/me/monitoring-plan")
    @PreAuthorize("hasRole('PATIENT')")
    public MonitoringPlanService.PatientPlanView monitoringPlan(Authentication authentication) {
        return monitoringPlans.patientPlan(accounts.patientId(authentication));
    }
}
