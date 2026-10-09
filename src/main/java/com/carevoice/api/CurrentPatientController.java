package com.carevoice.api;

import com.carevoice.auth.AccountService;
import com.carevoice.checkin.DailyCheckInService;
import com.carevoice.checkin.DailyCheckInStatusResponse;
import com.carevoice.plan.MonitoringPlanService;
import com.carevoice.service.MonitoringAgentService;
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
    public MonitoringController.SessionResponse start(Authentication authentication) {
        return MonitoringController.SessionResponse.from(sessions.startSession(accounts.patientId(authentication)));
    }

    @GetMapping("/api/me/check-in/today")
    @PreAuthorize("hasRole('PATIENT')")
    public DailyCheckInStatusResponse today(Authentication authentication) {
        return dailyCheckIns.status(accounts.patientId(authentication));
    }

    @PostMapping("/api/me/check-in/today")
    @PreAuthorize("hasRole('PATIENT')")
    public MonitoringController.SessionResponse startToday(Authentication authentication) {
        return MonitoringController.SessionResponse.from(dailyCheckIns.startToday(accounts.patientId(authentication)));
    }

    @GetMapping("/api/me/monitoring-plan")
    @PreAuthorize("hasRole('PATIENT')")
    public MonitoringPlanService.PatientPlanView monitoringPlan(Authentication authentication) {
        return monitoringPlans.patientPlan(accounts.patientId(authentication));
    }
}
