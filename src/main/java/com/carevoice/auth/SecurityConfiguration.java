package com.carevoice.auth;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(DemoClinicianProperties.class)
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(UserAccountRepository accounts, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(username -> {
            var account = accounts.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
            return User.withUsername(account.getUsername()).password(account.getPasswordHash()).roles(account.getRole().name()).disabled(!account.isEnabled()).build();
        });
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts, CsrfTokenRepository csrf) throws Exception {
        return http.cors(Customizer.withDefaults())
                .csrf(config -> config.csrfTokenRepository(csrf))
                .securityContext(config -> config.securityContextRepository(contexts))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable()).httpBasic(config -> config.disable())
                .authorizeHttpRequests(config -> config
                        .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/register/patient",
                                "/api/auth/login", "/api/auth/login/patient", "/api/auth/login/clinician").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/patients").denyAll()
                        .requestMatchers("/api/clinician/**").hasRole("CLINICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/patients/*/history", "/api/patients/*/longitudinal-summary",
                                "/api/monitoring/sessions/*/history").hasAnyRole("PATIENT", "CLINICIAN")
                        .requestMatchers("/api/**").hasRole("PATIENT").anyRequest().denyAll())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"Authentication required.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"Access denied.\"}");
                        }))
                .logout(config -> config.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                        .deleteCookies("JSESSIONID").logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .build();
    }
}
