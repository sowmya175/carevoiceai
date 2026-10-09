package com.carevoice.dto.monitoring;

import com.carevoice.domain.InputMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record PatientMessageRequest(
        @NotBlank @JsonAlias("transcript") String message,
        InputMode inputMode
) {}
