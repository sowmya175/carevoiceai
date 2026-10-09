package com.carevoice.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoClinicianInitializerTest {
    @Test
    void createsAHashedClinicianOnceAndDoesNotReplaceAnExistingPassword() {
        DemoClinicianProperties properties = new DemoClinicianProperties();
        properties.setEnabled(true);
        properties.setUsername("Doctor1");
        properties.setPassword("local-demo-password");
        UserAccountRepository accounts = org.mockito.Mockito.mock(UserAccountRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        when(accounts.existsByUsername("doctor1")).thenReturn(false);
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DemoClinicianInitializer initializer = new DemoClinicianInitializer(properties, accounts, encoder);
        initializer.createIfConfigured();

        var saved = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("doctor1");
        assertThat(saved.getValue().getRole()).isEqualTo(AccountRole.CLINICIAN);
        assertThat(saved.getValue().getPatient()).isNull();
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("local-demo-password");
        assertThat(encoder.matches("local-demo-password", saved.getValue().getPasswordHash())).isTrue();

        when(accounts.existsByUsername("doctor1")).thenReturn(true);
        initializer.createIfConfigured();
        verify(accounts).save(any(UserAccount.class));
    }

    @Test
    void staysDisabledUntilConfigured() {
        DemoClinicianProperties properties = new DemoClinicianProperties();
        UserAccountRepository accounts = org.mockito.Mockito.mock(UserAccountRepository.class);
        new DemoClinicianInitializer(properties, accounts, new BCryptPasswordEncoder(4)).createIfConfigured();
        verify(accounts, never()).save(any());
    }
}
