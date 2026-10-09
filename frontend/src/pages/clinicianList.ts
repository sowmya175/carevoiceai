import type { DailyCheckInStatus } from "../types/monitoring.ts";
import type { ClinicianPatient } from "../api/authApi.ts";

export type StatusFilter = "ALL" | DailyCheckInStatus;

const RANK: Record<DailyCheckInStatus, number> = {
  READY_FOR_REVIEW: 0,
  IN_PROGRESS: 1,
  NOT_STARTED: 2,
  COMPLETED: 3,
};

export function needsReview(patient: ClinicianPatient): boolean {
  return patient.requiresReview === true || patient.todayStatus === "READY_FOR_REVIEW";
}

export function monitoringFlagLabel(flag: string | null | undefined): string | null {
  switch (flag) {
    case "GREEN": return "Green monitoring flag";
    case "YELLOW": return "Yellow monitoring flag";
    case "RED": return "Red monitoring flag";
    default: return null;
  }
}

export function reminderLabel(enabled: boolean, time: string | null): string {
  if (!enabled || time == null) return "Off";
  const [hourText, minute] = time.split(":");
  const hour = Number(hourText);
  if (!Number.isInteger(hour) || minute == null) return "Off";
  const suffix = hour >= 12 ? "PM" : "AM";
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  return `Enabled — ${hour12}:${minute} ${suffix} local time`;
}

export function dashboardCounts(patients: ClinicianPatient[]) {
  return {
    patients: patients.length,
    notStarted: patients.filter((patient) => patient.todayStatus === "NOT_STARTED").length,
    inProgress: patients.filter((patient) => patient.todayStatus === "IN_PROGRESS").length,
    completed: patients.filter((patient) => patient.todayStatus === "COMPLETED").length,
    readyForReview: patients.filter((patient) => patient.todayStatus === "READY_FOR_REVIEW").length,
  };
}

export function visiblePatients(patients: ClinicianPatient[], query: string, filter: StatusFilter): ClinicianPatient[] {
  const needle = query.trim().toLowerCase();
  return patients.filter((patient) => {
    if (filter !== "ALL" && patient.todayStatus !== filter) return false;
    if (needle.length === 0) return true;
    return [patient.fullName, patient.medicalCondition, patient.monitoringPlanName]
      .filter((value): value is string => typeof value === "string")
      .join(" ")
      .toLowerCase()
      .includes(needle);
  }).sort((left, right) => {
    const rank = rankOf(left) - rankOf(right);
    if (rank !== 0) return rank;
    return left.fullName.localeCompare(right.fullName, undefined, { sensitivity: "base" });
  });
}

function rankOf(patient: ClinicianPatient): number {
  return patient.todayStatus == null ? 4 : RANK[patient.todayStatus];
}
