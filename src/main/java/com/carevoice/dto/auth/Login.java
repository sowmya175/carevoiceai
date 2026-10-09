package com.carevoice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Login(
        @NotBlank @Size(max = 32) String username,
        @NotBlank @Size(max = 72) String password
) {
    public Login {
        username = Usernames.normalize(username);
    }

    @Override
    public String toString() {
        return "Login[redacted]";
    }
}
