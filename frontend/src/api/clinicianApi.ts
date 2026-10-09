import { apiFetch, MonitoringApiError } from "./monitoringApi.ts";
import type { PatientLongitudinalResponse } from "../types/longitudinal.ts";
import { isLongitudinalResponse } from "./longitudinalResponse.ts";
import type { DailyCheckInStatus, RiskLevel, SessionStatus } from "../types/monitoring.ts";

export interface ClinicianOverview {
  patientId: number;
  fullName: string;
  medicalCondition: string | null;
  timezone: string | null;
  activeMonitoringPlanName: string | null;
  reminderEnabled: boolean;
  reminderTime: string | null;
  checkInDate: string | null;
  currentDate: string | null;
  todayStatus: DailyCheckInStatus;
  todaySessionId: number | null;
  sessionPlanName: string | null;
  startedAt: string | null;
  completedAt: string | null;
  monitoringFlag: RiskLevel | null;
  escalationReason: string | null;
  questionsAnswered: number;
  previousDaySession: boolean;
  requiresReview: boolean;
}

export interface ClinicianHistorySession {
  sessionId: number;
  startedAt: string;
  status: SessionStatus;
  riskLevel: RiskLevel;
  turnCount: number;
  checkInDate: string | null;
  monitoringPlanName: string | null;
  completedAt: string | null;
}

export interface ClinicianFact {
  label: string;
  value: string;
}

export interface ClinicianNote {
  noteText: string | null;
  noteLabel: string;
  monitoringFlag: RiskLevel | null;
  escalationReason: string | null;
  facts: ClinicianFact[];
}

export interface ClinicianTurn {
  sequence: number;
  question: string | null;
  patientResponse: string;
  inputMode: "TEXT" | "VOICE";
  createdAt: string;
  clinicalNote: ClinicianNote | null;
}

export interface ClinicianSession {
  sessionId: number;
  patientId: number;
  checkInDate: string | null;
  monitoringPlanName: string | null;
  status: SessionStatus;
  monitoringFlag: RiskLevel | null;
  escalationReason: string | null;
  startedAt: string | null;
  completedAt: string | null;
  sessionFacts: ClinicianFact[];
  turns: ClinicianTurn[];
}

export async function fetchClinicianOverview(patientId: number): Promise<ClinicianOverview> {
  const body = await read(`/api/clinician/patients/${patientId}`);
  if (!isOverview(body) || body.patientId !== patientId) {
    throw new MonitoringApiError(0, "Unable to load patient details.");
  }
  return body;
}

export async function fetchClinicianHistory(patientId: number): Promise<ClinicianHistorySession[]> {
  const body = await read(`/api/patients/${patientId}/history`);
  if (!isRecord(body) || body.patientId !== patientId || !Array.isArray(body.sessions) || !body.sessions.every(isHistorySession)) {
    throw new MonitoringApiError(0, "Unable to load patient details.");
  }
  return body.sessions;
}

export async function fetchClinicianTrends(patientId: number): Promise<PatientLongitudinalResponse> {
  const body = await read(`/api/patients/${patientId}/longitudinal-summary?limit=30`);
  if (!isLongitudinalResponse(body) || body.patientId !== patientId) {
    throw new MonitoringApiError(0, "Unable to load patient details.");
  }
  return body;
}

export async function fetchClinicianSession(sessionId: number): Promise<ClinicianSession> {
  const body = await read(`/api/clinician/sessions/${sessionId}`);
  if (!isSession(body) || body.sessionId !== sessionId) {
    throw new MonitoringApiError(0, "Unable to load this check-in.");
  }
  return body;
}

async function read(path: string): Promise<unknown> {
  const response = await apiFetch(path, { headers: { Accept: "application/json" }, cache: "no-store" });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new MonitoringApiError(response.status, response.status === 404
      ? "Unable to load patient details."
      : "Unable to load patient details.");
  }
  return body;
}

function isOverview(value: unknown): value is ClinicianOverview {
  if (!isRecord(value)) return false;
  return typeof value.patientId === "number"
    && typeof value.fullName === "string"
    && typeof value.todayStatus === "string"
    && typeof value.questionsAnswered === "number"
    && typeof value.reminderEnabled === "boolean";
}

function isHistorySession(value: unknown): value is ClinicianHistorySession {
  if (!isRecord(value)) return false;
  return typeof value.sessionId === "number"
    && typeof value.startedAt === "string"
    && typeof value.status === "string"
    && typeof value.turnCount === "number";
}

function isSession(value: unknown): value is ClinicianSession {
  if (!isRecord(value)) return false;
  return typeof value.sessionId === "number"
    && typeof value.patientId === "number"
    && Array.isArray(value.turns)
    && Array.isArray(value.sessionFacts);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
