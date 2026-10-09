package com.carevoice.plan;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.SessionPlanQuestion;

import java.util.List;

/**
 * Questions that belong to one monitoring session.
 * A captured snapshot stays in force for that session.
 * A session with no snapshot, including rows created before plans existed,
 * uses the General Daily Wellness definition. New sessions always capture a copy.
 */
public final class SessionMonitoringPlan {
    private SessionMonitoringPlan() {}

    public static List<PlanField> fields(MonitoringSession session) {
        if (session == null || session.getPlanQuestions() == null || session.getPlanQuestions().isEmpty()) {
            return DemoMonitoringPlans.general().questions();
        }
        return session.getPlanQuestions().stream().map(SessionPlanQuestion::toPlanField).toList();
    }
}
