package com.carevoice.service;

import com.carevoice.checkin.DailyCheckInService;
import com.carevoice.domain.MonitoringSession;
import org.springframework.stereotype.Service;

@Service
public class MonitoringAgentService {
    public static final String OPENING_QUESTION = "Tell me how you are feeling today in your own words.";

    private final DailyCheckInService dailyCheckIns;

    public MonitoringAgentService(DailyCheckInService dailyCheckIns) {
        this.dailyCheckIns = dailyCheckIns;
    }

    public MonitoringSession startSession(Long patientId) {
        return dailyCheckIns.startToday(patientId);
    }
}
