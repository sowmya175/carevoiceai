package com.carevoice.config;
import com.carevoice.domain.AccountRole;
import com.carevoice.service.AccountService;
import com.carevoice.domain.UserAccount;
import com.carevoice.repository.UserAccountRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo/development only. Creates one clinician when explicitly enabled.
 * An existing username is left unchanged, including its password.
 */
@Component
@Order(10)
public class DemoClinicianInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoClinicianInitializer.class);
    private final DemoClinicianProperties properties;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;

    public DemoClinicianInitializer(DemoClinicianProperties properties, UserAccountRepository accounts, PasswordEncoder passwords) {
        this.properties = properties; this.accounts = accounts; this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) { createIfConfigured(); }

    public void createIfConfigured() {
        if (!properties.isEnabled()) return;
        String username = AccountService.normalizeUsername(properties.getUsername());
        String password = properties.getPassword() == null ? "" : properties.getPassword();
        if (!AccountService.acceptableUsername(username) || password.isBlank()) {
            log.warn("Demo clinician setup is enabled, but the username or password is missing. No clinician account was created.");
            return;
        }
        if (accounts.existsByUsername(username)) {
            log.info("Demo clinician username already exists. The existing password was left unchanged.");
            return;
        }
        accounts.save(new UserAccount(username, passwords.encode(password), AccountRole.CLINICIAN, null));
        log.info("Created a demo clinician account for username {}.", username);
    }
}
