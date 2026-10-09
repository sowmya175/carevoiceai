package com.carevoice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Registration(
        @NotBlank @Size(max = 32) String username,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 255) String medicalCondition,
        @NotBlank @Size(max = 64) String timezone
) {
    public Registration {
        username = Usernames.normalize(username);
        fullName = trim(fullName);
        medicalCondition = trim(medicalCondition);
        timezone = trim(timezone);
    }

    @Override
    public String toString() {
        return "Registration[redacted]";
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
