package com.carevoice.checkin;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DailyCheckInStatusResponse(
        LocalDate checkInDate,
        LocalDate currentDate,
        String timezone,
        DailyCheckInStatus status,
        Long sessionId,
        String monitoringPlanName,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        boolean previousDaySession
) {}
