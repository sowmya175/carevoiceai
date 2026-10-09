package com.carevoice.auth;

import com.carevoice.condition.PatientConditionService;
import com.carevoice.domain.Patient;
import com.carevoice.plan.MonitoringPlanService;
import com.carevoice.reminder.ReminderPreferenceService;
import com.carevoice.repository.PatientRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final UserAccountRepository accounts;
    private final PatientRepository patients;
    private final PasswordEncoder passwords;
    private final MonitoringPlanService monitoringPlans;
    private final ReminderPreferenceService reminderPreferences;
    private final PatientConditionService conditions;
    public AccountService(
            UserAccountRepository accounts,
            PatientRepository patients,
            PasswordEncoder passwords,
            MonitoringPlanService monitoringPlans,
            ReminderPreferenceService reminderPreferences,
            PatientConditionService conditions) {
        this.accounts = accounts;
        this.patients = patients;
        this.passwords = passwords;
        this.monitoringPlans = monitoringPlans;
        this.reminderPreferences = reminderPreferences;
        this.conditions = conditions;
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean acceptableUsername(String normalized) {
        return normalized != null && normalized.matches("[a-z0-9][a-z0-9._-]{2,31}");
    }

    @Transactional
    public CurrentUser registerPatient(AuthController.Registration request) {
        if (!acceptableUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a username of 3–32 letters, numbers, dots, underscores, or hyphens.");
        }
        if (!ZoneId.getAvailableZoneIds().contains(request.timezone())
                || !(request.timezone().contains("/") || request.timezone().equals("UTC"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid IANA timezone.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at most 72 UTF-8 bytes.");
        }
        if (accounts.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
        Patient patient = patients.save(new Patient(request.fullName(), request.medicalCondition(), request.timezone()));
        conditions.ensureInitial(patient.getId());
        monitoringPlans.assignGeneralIfMissing(patient);
        reminderPreferences.ensureDefault(patient);
        UserAccount account = accounts.saveAndFlush(new UserAccount(
                request.username(), passwords.encode(request.password()), AccountRole.PATIENT, patient));
        return dto(account, false);
    }

    public UserAccount current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new AccessDeniedException("Authentication required.");
        return accounts.findByUsername(authentication.getName()).filter(UserAccount::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable."));
    }
    public Long patientId(Authentication authentication) {
        UserAccount account = current(authentication);
        if (account.getRole() != AccountRole.PATIENT || account.getPatient() == null) throw new AccessDeniedException("Patient account required.");
        return account.getPatient().getId();
    }
    public CurrentUser me(Authentication authentication) { return dto(current(authentication), true); }
    private CurrentUser dto(UserAccount account, boolean authenticated) {
        Patient patient = account.getPatient();
        return new CurrentUser(authenticated, account.getId(), account.getUsername(), account.getRole(), patient == null ? null :
                new Profile(patient.getId(), patient.getFullName(), patient.getMedicalCondition(), patient.getTimezone(),
                        patient.getCreatedAt(), patient.getUpdatedAt()));
    }
    public record CurrentUser(boolean authenticated, Long accountId, String username, AccountRole role, Profile patient) {}
    public record Profile(Long id, String fullName, String medicalCondition, String timezone, Instant createdAt, Instant updatedAt) {}
}
