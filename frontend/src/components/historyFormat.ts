import type { SessionStatus } from "../types/monitoring.ts";

export function formatDate(timestamp: string | null, withTime = false): string {
  if (!timestamp || !Number.isFinite(Date.parse(timestamp))) return "Not available";
  return new Intl.DateTimeFormat(undefined, {
    month: "short", day: "numeric", year: "numeric",
    ...(withTime ? { hour: "numeric", minute: "2-digit" } as const : {}),
  }).format(new Date(timestamp));
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
