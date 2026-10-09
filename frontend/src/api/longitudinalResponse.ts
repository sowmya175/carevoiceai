import type { PatientLongitudinalResponse } from "../types/longitudinal.ts";

const record = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null;
const number = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value);
const count = (value: unknown) => number(value) && Number.isInteger(value) && value >= 0;
const id = (value: unknown) => count(value) && (value as number) > 0;
const date = (value: unknown) => typeof value === "string" && Number.isFinite(Date.parse(value));
const calendarDate = (value: unknown) => typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value);
const nullableDate = (value: unknown) => value === null || date(value);
const nullableNumber = (value: unknown) => value === null || number(value);
const boolean = (value: unknown) => value === null || typeof value === "boolean";
const category = (allowed: string[]) => (value: unknown) => value === null || allowed.includes(value as string);

function observations(value: unknown, validValue: (value: unknown) => boolean): boolean {
  return Array.isArray(value) && value.every((item) => record(item) && id(item.sessionId)
    && date(item.timestamp) && validValue(item.value));
}

function counts(value: unknown, keys: string[], validValue: (value: unknown) => boolean): boolean {
  return record(value) && keys.every((key) => count(value[key])) && observations(value.observations, validValue);
}

function symptom(value: unknown): boolean {
  return record(value) && counts(value, ["reportedTrueCount", "reportedFalseCount", "unknownCount"], boolean)
    && nullableDate(value.firstReportedAt) && nullableDate(value.lastReportedAt);
}

/** Reject incomplete payloads rather than inventing zero counts or silently dropping observations. */
export function isLongitudinalResponse(value: unknown): value is PatientLongitudinalResponse {
  if (!record(value) || !id(value.patientId) || !count(value.sessionCount)
    || !nullableDate(value.windowStart) || !nullableDate(value.windowEnd) || !nullableDate(value.latestCheckInAt)) return false;
  const pain = value.pain;
  return record(pain) && count(pain.observationCount) && nullableNumber(pain.firstValue)
    && nullableNumber(pain.latestValue) && nullableNumber(pain.change)
    && observations(pain.observations, (value) => number(value) && Number.isInteger(value) && value >= 0 && value <= 10)
    && counts(value.sleep, ["goodCount", "normalCount", "poorCount", "unknownCount"], category(["good", "normal", "poor"]))
    && counts(value.appetite, ["goodCount", "normalCount", "reducedCount", "poorCount", "unknownCount"], category(["good", "normal", "reduced", "poor"]))
    && counts(value.medication, ["takenCount", "missedCount", "unknownCount"], boolean)
    && record(value.symptoms) && symptom(value.symptoms.dizziness)
    && symptom(value.symptoms.shortnessOfBreath) && symptom(value.symptoms.lossOfConsciousness)
    && observations(value.temperature, number)
    && Array.isArray(value.dizzinessOnset) && value.dizzinessOnset.every((item) => record(item)
      && id(item.sessionId) && date(item.timestamp) && typeof item.onset === "string")
    && Array.isArray(value.sessions) && value.sessions.length === value.sessionCount
    && value.sessions.every((item) => record(item) && id(item.sessionId) && date(item.startedAt)
      && ["COMPLETED", "READY_FOR_REVIEW", "IN_PROGRESS"].includes(item.status as string) && count(item.turnCount)
      && ["GREEN", "YELLOW", "RED"].includes(item.riskLevel as string)
      && (item.checkInDate === undefined || item.checkInDate === null || calendarDate(item.checkInDate))
      && (item.monitoringPlanName === undefined || item.monitoringPlanName === null || typeof item.monitoringPlanName === "string"));
}
