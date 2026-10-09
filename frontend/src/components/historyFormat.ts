import type { DailyCheckInStatus, SessionStatus } from "../types/monitoring.ts";

export function formatDate(timestamp: string | null, withTime = false): string {
  if (!timestamp || !Number.isFinite(Date.parse(timestamp))) return "Not available";
  return new Intl.DateTimeFormat(undefined, {
    month: "short", day: "numeric", year: "numeric",
    ...(withTime ? { hour: "numeric", minute: "2-digit" } as const : {}),
  }).format(new Date(timestamp));
}

/** Patient-local calendar date. A date-only string is not parsed as UTC midnight. */
export function formatCalendarDate(isoDate: string | null | undefined): string {
  if (!isoDate || !/^\d{4}-\d{2}-\d{2}$/.test(isoDate)) return "Not available";
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Intl.DateTimeFormat("en-US", { month: "long", day: "numeric", year: "numeric" })
    .format(new Date(year, month - 1, day));
}

export function dailyStatusLabel(status: DailyCheckInStatus): string {
  switch (status) {
    case "NOT_STARTED": return "Not started";
    case "IN_PROGRESS": return "In progress";
    case "COMPLETED": return "Completed";
    case "READY_FOR_REVIEW": return "Ready for review";
    default: return "Status unavailable";
  }
}

export function statusLabel(status: SessionStatus): string {
  switch (status) {
    case "COMPLETED": return "Completed";
    case "READY_FOR_REVIEW": return "Submitted for review";
    case "IN_PROGRESS": return "In progress";
    default: return "Status unavailable";
  }
}

export function categoryLabel(value: string | null): string {
  return value === null ? "Unknown" : value.charAt(0).toUpperCase() + value.slice(1);
}
