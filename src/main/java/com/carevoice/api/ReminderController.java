package com.carevoice.api;

import com.carevoice.auth.AccountService;
import com.carevoice.reminder.ReminderInboxService;
import com.carevoice.reminder.ReminderPreferenceService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReminderController {
    private final AccountService accounts;
    private final ReminderPreferenceService preferences;
    private final ReminderInboxService inbox;

    public ReminderController(
            AccountService accounts,
            ReminderPreferenceService preferences,
            ReminderInboxService inbox) {
        this.accounts = accounts;
        this.preferences = preferences;
        this.inbox = inbox;
    }

    @GetMapping("/api/me/reminder-preference")
    @PreAuthorize("hasRole('PATIENT')")
    public ReminderPreferenceService.ReminderPreferenceResponse preference(Authentication authentication) {
        return preferences.get(accounts.patientId(authentication));
    }

    @PutMapping("/api/me/reminder-preference")
    @PreAuthorize("hasRole('PATIENT')")
    public ReminderPreferenceService.ReminderPreferenceResponse updatePreference(
            Authentication authentication,
            @RequestBody ReminderPreferenceRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid reminder time.");
        }
        return preferences.update(accounts.patientId(authentication), request.enabled(), request.reminderTime());
    }

    @GetMapping("/api/me/notifications")
    @PreAuthorize("hasRole('PATIENT')")
    public List<ReminderInboxService.InAppNotificationResponse> notifications(Authentication authentication) {
        return inbox.list(accounts.patientId(authentication));
    }

    @PostMapping("/api/me/notifications/{id}/read")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication authentication, @PathVariable Long id) {
        inbox.markRead(accounts.patientId(authentication), id);
    }

    public record ReminderPreferenceRequest(Boolean enabled, String reminderTime) {}
}
