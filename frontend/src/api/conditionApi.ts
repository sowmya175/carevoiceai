import { apiFetch, MonitoringApiError } from "./monitoringApi.ts";

export type MonitoringCategory =
  | "WELLNESS"
  | "POST_OPERATIVE"
  | "HYPERTENSION"
  | "DIABETES"
  | "CARDIAC"
  | "RESPIRATORY"
  | "OTHER";

export interface PatientConditionSummary {
  conditionName: string;
  monitoringCategory: MonitoringCategory;
  primary: boolean;
}

export interface ClinicianCondition {
  id: number;
  conditionName: string;
  monitoringCategory: MonitoringCategory;
  active: boolean;
  primaryCondition: boolean;
}

export const CONDITION_CATEGORIES: { id: MonitoringCategory; label: string }[] = [
  { id: "WELLNESS", label: "Wellness" },
  { id: "POST_OPERATIVE", label: "Post-operative" },
  { id: "HYPERTENSION", label: "Hypertension" },
  { id: "DIABETES", label: "Diabetes" },
  { id: "CARDIAC", label: "Cardiac" },
  { id: "RESPIRATORY", label: "Respiratory" },
  { id: "OTHER", label: "Other" },
];

export function categoryLabel(category: string | null | undefined): string {
  return CONDITION_CATEGORIES.find((item) => item.id === category)?.label ?? "Other";
}

export async function fetchMyConditions(): Promise<PatientConditionSummary[]> {
  const body = await read("/api/me/conditions", "Unable to load health conditions.");
  if (!Array.isArray(body) || !body.every(isPatientCondition)) {
    throw new MonitoringApiError(0, "Unable to load health conditions.");
  }
  return body;
}

export async function fetchClinicianConditions(patientId: number): Promise<ClinicianCondition[]> {
  const body = await read(`/api/clinician/patients/${patientId}/conditions`, "Unable to load conditions.");
  if (!Array.isArray(body) || !body.every(isClinicianCondition)) {
    throw new MonitoringApiError(0, "Unable to load conditions.");
  }
  return body;
}

export async function createClinicianCondition(
  patientId: number,
  conditionName: string,
  monitoringCategory: MonitoringCategory,
  primaryCondition: boolean,
): Promise<ClinicianCondition> {
  return send(`/api/clinician/patients/${patientId}/conditions`, "POST", {
    conditionName, monitoringCategory, primaryCondition,
  });
}

export async function updateClinicianCondition(
  patientId: number,
  conditionId: number,
  conditionName: string,
  monitoringCategory: MonitoringCategory,
  active: boolean,
  primaryCondition: boolean,
): Promise<ClinicianCondition> {
  return send(`/api/clinician/patients/${patientId}/conditions/${conditionId}`, "PUT", {
    conditionName, monitoringCategory, active, primaryCondition,
  });
}

async function send(path: string, method: "POST" | "PUT", payload: unknown): Promise<ClinicianCondition> {
  const response = await apiFetch(path, {
    method,
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !isClinicianCondition(body)) {
    throw new MonitoringApiError(response.status, message(body, "Unable to save that condition."));
  }
  return body;
}

async function read(path: string, fallback: string): Promise<unknown> {
  const response = await apiFetch(path, { headers: { Accept: "application/json" }, cache: "no-store" });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new MonitoringApiError(response.status, message(body, fallback));
  }
  return body;
}

function message(body: unknown, fallback: string): string {
  if (typeof body === "object" && body !== null && "error" in body && typeof body.error === "string" && body.error.trim().length > 0) {
    return body.error;
  }
  return fallback;
}

function isPatientCondition(value: unknown): value is PatientConditionSummary {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.conditionName === "string"
    && typeof record.monitoringCategory === "string"
    && typeof record.primary === "boolean";
}

function isClinicianCondition(value: unknown): value is ClinicianCondition {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.id === "number"
    && typeof record.conditionName === "string"
    && typeof record.monitoringCategory === "string"
    && typeof record.active === "boolean"
    && typeof record.primaryCondition === "boolean";
}
