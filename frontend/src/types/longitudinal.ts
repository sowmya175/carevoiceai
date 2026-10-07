import type { RiskLevel, SessionStatus } from "./monitoring.ts";

export interface Observation<T> { sessionId: number; timestamp: string; value: T }
export interface PainSummary {
  observationCount: number;
  firstValue: number | null;
  latestValue: number | null;
  change: number | null;
  observations: Observation<number>[];
}
export interface SleepSummary {
  goodCount: number; normalCount: number; poorCount: number; unknownCount: number;
  observations: Observation<"good" | "normal" | "poor" | null>[];
}
export interface AppetiteSummary {
  goodCount: number; normalCount: number; reducedCount: number; poorCount: number; unknownCount: number;
  observations: Observation<"good" | "normal" | "reduced" | "poor" | null>[];
}
export interface MedicationSummary {
  takenCount: number; missedCount: number; unknownCount: number;
  observations: Observation<boolean | null>[];
}
export interface SymptomSummary {
  reportedTrueCount: number; reportedFalseCount: number; unknownCount: number;
  firstReportedAt: string | null; lastReportedAt: string | null;
  observations: Observation<boolean | null>[];
}
export interface CheckInSummary {
  sessionId: number; startedAt: string; status: SessionStatus; riskLevel: RiskLevel; turnCount: number;
}
export interface PatientLongitudinalResponse {
  patientId: number;
  windowStart: string | null;
  windowEnd: string | null;
  sessionCount: number;
  latestCheckInAt: string | null;
  pain: PainSummary;
  sleep: SleepSummary;
  appetite: AppetiteSummary;
  medication: MedicationSummary;
  symptoms: { dizziness: SymptomSummary; shortnessOfBreath: SymptomSummary; lossOfConsciousness: SymptomSummary };
  dizzinessOnset: { sessionId: number; timestamp: string; onset: string }[];
  temperature: Observation<number>[];
  sessions: CheckInSummary[];
}
