package com.carevoice.api;

import com.carevoice.auth.AccountService;
import com.carevoice.condition.PatientConditionService;
import com.carevoice.condition.PatientConditionService.PatientConditionSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PatientConditionController {
    private final AccountService accounts;
    private final PatientConditionService conditions;

    public PatientConditionController(AccountService accounts, PatientConditionService conditions) {
        this.accounts = accounts;
        this.conditions = conditions;
    }

    @GetMapping("/api/me/conditions")
    @PreAuthorize("hasRole('PATIENT')")
    public List<PatientConditionSummary> mine(Authentication authentication) {
        return conditions.listActiveForPatient(accounts.patientId(authentication));
    }
}
