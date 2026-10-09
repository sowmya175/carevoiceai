import { apiFetch, MonitoringApiError, resetCsrf } from "./monitoringApi.ts";

export interface PatientProfile {
  id: number;
  fullName: string;
  medicalCondition: string;
  timezone: string;
}

export interface CurrentUser {
  authenticated: boolean;
  accountId: number;
  username: string;
  role: "PATIENT" | "CLINICIAN" | "ADMIN";
  patient: PatientProfile | null;
}

export interface RegistrationInput {
  fullName: string;
  username: string;
  password: string;
  medicalCondition: string;
  timezone: string;
}

export interface ClinicianPatient {
  patientId: number;
  fullName: string;
  medicalCondition: string | null;
  timezone: string | null;
  monitoringPlanName: string | null;
  todayCheckInDate?: string | null;
  todayStatus?: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "READY_FOR_REVIEW" | null;
  todaySessionId?: number | null;
  latestMonitoringFlag?: "GREEN" | "YELLOW" | "RED" | null;
  latestCheckInAt?: string | null;
  requiresReview?: boolean;
}

export interface MonitoringPlanChoice {
  id: number;
  name: string;
  description: string;
  conditionLabel: string;
}

export interface PatientMonitoringPlan {
  name: string;
  description: string;
}

export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const response = await apiFetch("/api/auth/me", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (response.status === 401) {
    return null;
  }
  const body = await response.json().catch(() => null);
  if (!response.ok || !isCurrentUser(body) || !body.authenticated) {
    throw new MonitoringApiError(response.status, "We couldn't confirm your session. Please try again.");
  }
  return body;
}

export async function registerAccount(input: RegistrationInput): Promise<void> {
  const response = await apiFetch("/api/auth/register/patient", {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!response.ok) {
    throw new MonitoringApiError(response.status, await errorText(response, "We couldn't create that account. Please try again."));
  }
}

export async function loginPatient(username: string, password: string): Promise<void> {
  await login("/api/auth/login/patient", username, password);
}

export async function loginClinician(username: string, password: string): Promise<void> {
  await login("/api/auth/login/clinician", username, password);
}

export async function fetchClinicianPatients(): Promise<ClinicianPatient[]> {
  const response = await apiFetch("/api/clinician/patients", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !Array.isArray(body) || !body.every(isClinicianPatient)) {
    throw new MonitoringApiError(response.status, "We couldn't load the patient list. Please try again.");
  }
  return body;
}

export async function fetchMyMonitoringPlan(): Promise<PatientMonitoringPlan> {
  const response = await apiFetch("/api/me/monitoring-plan", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !isPatientPlan(body)) {
    throw new MonitoringApiError(response.status, "We couldn't load your monitoring plan. Please try again.");
  }
  return body;
}

export async function fetchMonitoringPlans(): Promise<MonitoringPlanChoice[]> {
  const response = await apiFetch("/api/clinician/monitoring-plans", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !Array.isArray(body) || !body.every(isPlanChoice)) {
    throw new MonitoringApiError(response.status, "We couldn't load monitoring plans. Please try again.");
  }
  return body;
}

export async function assignMonitoringPlan(patientId: number, monitoringPlanId: number): Promise<PatientMonitoringPlan> {
  const response = await apiFetch(`/api/clinician/patients/${patientId}/monitoring-plan`, {
    method: "PUT",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ monitoringPlanId }),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !isPatientPlan(body)) {
    throw new MonitoringApiError(response.status, "We couldn't assign that monitoring plan. Please try again.");
  }
  return body;
}

export async function logout(): Promise<void> {
  const response = await apiFetch("/api/auth/logout", {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  resetCsrf();
  if (!response.ok) {
    throw new MonitoringApiError(response.status, "We couldn't sign you out. Please try again.");
  }
}

async function login(path: string, username: string, password: string): Promise<void> {
  const response = await apiFetch(path, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  resetCsrf();
  if (!response.ok) {
    throw new MonitoringApiError(response.status, await errorText(response, "Invalid username or password."));
  }
}

async function errorText(response: Response, fallback: string): Promise<string> {
  const body: unknown = await response.json().catch(() => null);
  if (typeof body === "object" && body !== null && "error" in body && typeof body.error === "string") {
    const message = body.error.trim();
    if (message.length > 0 && message.length <= 240 && !message.includes("\n")) {
      return message;
    }
  }
  return fallback;
}

function isCurrentUser(value: unknown): value is CurrentUser {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.authenticated === "boolean"
    && typeof record.accountId === "number"
    && typeof record.username === "string"
    && (record.role === "PATIENT" || record.role === "CLINICIAN" || record.role === "ADMIN")
    && (record.patient === null || isProfile(record.patient));
}

function isProfile(value: unknown): value is PatientProfile {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.id === "number"
    && typeof record.fullName === "string"
    && typeof record.medicalCondition === "string"
    && typeof record.timezone === "string";
}

function isClinicianPatient(value: unknown): value is ClinicianPatient {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.patientId === "number"
    && typeof record.fullName === "string"
    && (record.medicalCondition === null || typeof record.medicalCondition === "string")
    && (record.timezone === null || typeof record.timezone === "string")
    && (record.monitoringPlanName === undefined || record.monitoringPlanName === null || typeof record.monitoringPlanName === "string")
    && (record.todayStatus === undefined || record.todayStatus === null
      || record.todayStatus === "NOT_STARTED" || record.todayStatus === "IN_PROGRESS"
      || record.todayStatus === "COMPLETED" || record.todayStatus === "READY_FOR_REVIEW");
}

function isPatientPlan(value: unknown): value is PatientMonitoringPlan {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.name === "string" && typeof record.description === "string";
}

function isPlanChoice(value: unknown): value is MonitoringPlanChoice {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.id === "number"
    && typeof record.name === "string"
    && typeof record.description === "string"
    && typeof record.conditionLabel === "string";
}
