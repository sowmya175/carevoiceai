package com.carevoice.auth;

import com.carevoice.repository.MonitoringSessionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("patientAccess")
public class PatientAccess {
    private final AccountService accounts;
    private final MonitoringSessionRepository sessions;
    public PatientAccess(AccountService accounts, MonitoringSessionRepository sessions) {
        this.accounts = accounts; this.sessions = sessions;
    }
    public boolean ownsPatient(Long patientId, Authentication authentication) {
        try { return accounts.patientId(authentication).equals(patientId); }
        catch (AccessDeniedException e) { return false; }
    }
    public boolean ownsSession(Long sessionId, Authentication authentication) {
        try { return sessions.existsByIdAndPatient_Id(sessionId, accounts.patientId(authentication)); }
        catch (AccessDeniedException e) { return false; }
    }
    public boolean canReadPatient(Long patientId, Authentication authentication) {
        UserAccount account = readableAccount(authentication);
        if (account == null) return false;
        if (account.getRole() == AccountRole.CLINICIAN) return true;
        return account.getRole() == AccountRole.PATIENT && account.getPatient() != null
                && account.getPatient().getId().equals(patientId);
    }
    public boolean canReadSession(Long sessionId, Authentication authentication) {
        UserAccount account = readableAccount(authentication);
        if (account == null) return false;
        if (account.getRole() == AccountRole.CLINICIAN) return true;
        return account.getRole() == AccountRole.PATIENT && account.getPatient() != null
                && sessions.existsByIdAndPatient_Id(sessionId, account.getPatient().getId());
    }
    private UserAccount readableAccount(Authentication authentication) {
        try { return accounts.current(authentication); }
        catch (AccessDeniedException e) { return null; }
    }
}
