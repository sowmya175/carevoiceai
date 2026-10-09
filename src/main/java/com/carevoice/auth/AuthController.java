package com.carevoice.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    public AuthController(AccountService accounts, AuthenticationManager authenticationManager,
                          SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.accounts = accounts; this.authenticationManager = authenticationManager; this.contexts = contexts; this.sessions = sessions;
    }
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }
    @PostMapping({"/register", "/register/patient"}) @ResponseStatus(HttpStatus.CREATED)
    public AccountService.CurrentUser register(@Valid @RequestBody Registration registration) {
        return accounts.registerPatient(registration);
    }
    @PostMapping({"/login", "/login/patient"})
    public AccountService.CurrentUser loginPatient(@Valid @RequestBody Login login, HttpServletRequest request, HttpServletResponse response) {
        return login(login, AccountRole.PATIENT, request, response);
    }
    @PostMapping("/login/clinician")
    public AccountService.CurrentUser loginClinician(@Valid @RequestBody Login login, HttpServletRequest request, HttpServletResponse response) {
        return login(login, AccountRole.CLINICIAN, request, response);
    }
    @GetMapping("/me")
    public AccountService.CurrentUser me(Authentication authentication) { return accounts.me(authentication); }

    private AccountService.CurrentUser login(Login login, AccountRole expected, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        if (accounts.current(authentication).getRole() != expected) {
            SecurityContextHolder.clearContext();
            throw new BadCredentialsException("Invalid username or password.");
        }
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return accounts.me(authentication);
    }

    public record CsrfResponse(String headerName, String token) {}
    public record Registration(@NotBlank @Size(max = 32) String username,
                               @NotBlank @Size(min = 12, max = 72) String password,
                               @NotBlank @Size(max = 120) String fullName,
                               @NotBlank @Size(max = 255) String medicalCondition,
                               @NotBlank @Size(max = 64) String timezone) {
        public Registration {
            username = AccountService.normalizeUsername(username);
            fullName = trim(fullName); medicalCondition = trim(medicalCondition); timezone = trim(timezone);
        }
        @Override public String toString() { return "Registration[redacted]"; }
    }
    public record Login(@NotBlank @Size(max = 32) String username, @NotBlank @Size(max = 72) String password) {
        public Login { username = AccountService.normalizeUsername(username); }
        @Override public String toString() { return "Login[redacted]"; }
    }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}
