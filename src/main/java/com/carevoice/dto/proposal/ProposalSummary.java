package com.carevoice.dto.proposal;

import java.time.Instant;

public record ProposalSummary(Long id, Long patientId, String status, Instant createdAt) {}
