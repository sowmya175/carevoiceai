import type { Observation, PatientLongitudinalResponse } from "../types/longitudinal.ts";
import type { SessionHistory } from "../types/monitoring.ts";

function observations<T>(values: T[]): Observation<T>[] {
  return values.map((value, index) => ({ sessionId: 101 + index, timestamp: `2026-10-0${index + 1}T13:00:00Z`, value }));
}

export function historySummary(): PatientLongitudinalResponse {
  return {
    patientId: 7, windowStart: "2026-10-01T13:00:00Z", windowEnd: "2026-10-03T13:00:00Z",
    sessionCount: 3, latestCheckInAt: "2026-10-03T13:00:00Z",
    pain: { observationCount: 3, firstValue: 2, latestValue: 6, change: 4, observations: observations([2, 4, 6]) },
    sleep: { goodCount: 1, normalCount: 0, poorCount: 1, unknownCount: 1, observations: observations(["good", "poor", null]) },
    appetite: { goodCount: 0, normalCount: 1, reducedCount: 1, poorCount: 0, unknownCount: 1, observations: observations(["normal", "reduced", null]) },
    medication: { takenCount: 1, missedCount: 1, unknownCount: 1, observations: observations([true, false, null]) },
    symptoms: {
      dizziness: { reportedTrueCount: 1, reportedFalseCount: 1, unknownCount: 1,
        firstReportedAt: "2026-10-02T13:00:00Z", lastReportedAt: "2026-10-02T13:00:00Z", observations: observations([false, true, null]) },
      shortnessOfBreath: { reportedTrueCount: 0, reportedFalseCount: 0, unknownCount: 3,
        firstReportedAt: null, lastReportedAt: null, observations: observations([null, null, null]) },
      lossOfConsciousness: { reportedTrueCount: 0, reportedFalseCount: 3, unknownCount: 0,
        firstReportedAt: null, lastReportedAt: null, observations: observations([false, false, false]) },
    },
    dizzinessOnset: [{ sessionId: 102, timestamp: "2026-10-02T13:00:00Z", onset: "This morning when I stood up." }],
    temperature: observations([37, 98.6]),
    sessions: [
      { sessionId: 101, startedAt: "2026-10-01T13:00:00Z", status: "COMPLETED", riskLevel: "GREEN", turnCount: 4 },
      { sessionId: 102, startedAt: "2026-10-02T13:00:00Z", status: "COMPLETED", riskLevel: "YELLOW", turnCount: 5 },
      { sessionId: 103, startedAt: "2026-10-03T13:00:00Z", status: "READY_FOR_REVIEW", riskLevel: "RED", turnCount: 2 },
    ],
  };
}

export function emptySummary(): PatientLongitudinalResponse {
  return {
    patientId: 7, windowStart: null, windowEnd: null, sessionCount: 0, latestCheckInAt: null,
    pain: { observationCount: 0, firstValue: null, latestValue: null, change: null, observations: [] },
    sleep: { goodCount: 0, normalCount: 0, poorCount: 0, unknownCount: 0, observations: [] },
    appetite: { goodCount: 0, normalCount: 0, reducedCount: 0, poorCount: 0, unknownCount: 0, observations: [] },
    medication: { takenCount: 0, missedCount: 0, unknownCount: 0, observations: [] },
    symptoms: {
      dizziness: { reportedTrueCount: 0, reportedFalseCount: 0, unknownCount: 0, firstReportedAt: null, lastReportedAt: null, observations: [] },
      shortnessOfBreath: { reportedTrueCount: 0, reportedFalseCount: 0, unknownCount: 0, firstReportedAt: null, lastReportedAt: null, observations: [] },
      lossOfConsciousness: { reportedTrueCount: 0, reportedFalseCount: 0, unknownCount: 0, firstReportedAt: null, lastReportedAt: null, observations: [] },
    },
    dizzinessOnset: [], temperature: [], sessions: [],
  };
}

export function sessionHistory(sessionId = 103): SessionHistory {
  return {
    patientId: 7, sessionId, startedAt: "2026-10-03T13:00:00Z", status: "READY_FOR_REVIEW",
    conversationComplete: true, currentQuestion: null,
    turns: [{ sequenceNumber: 1, timestamp: "2026-10-03T13:01:00Z", question: "How did you sleep?",
      patientResponse: "I kept waking up.", inputMode: "VOICE", clinicalNote: "PRIVATE CLINICAL NOTE", riskLevel: "RED" }],
  };
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
