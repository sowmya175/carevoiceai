import { apiFetch, MonitoringApiError } from "./monitoringApi.ts";

export interface ReminderPreference {
  enabled: boolean;
  reminderTime: string;
  timezone: string;
}

export interface InAppNotification {
  id: number;
  type: string;
  messageKey: string;
  message: string;
  createdAt: string;
  read: boolean;
}

export async function fetchReminderPreference(): Promise<ReminderPreference> {
  const response = await apiFetch("/api/me/reminder-preference", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  const body = await response.json() as unknown;
  if (!response.ok || !isPreference(body)) {
    throw new MonitoringApiError(response.status, "We couldn't load reminder settings.");
  }
  return body;
}

export async function saveReminderPreference(enabled: boolean, reminderTime: string): Promise<ReminderPreference> {
  const response = await apiFetch("/api/me/reminder-preference", {
    method: "PUT",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ enabled, reminderTime }),
  });
  const body = await response.json() as unknown;
  if (!response.ok || !isPreference(body)) {
    throw new MonitoringApiError(response.status, "We couldn't save your reminder settings. Please try again.");
  }
  return body;
}

export async function fetchNotifications(): Promise<InAppNotification[]> {
  const response = await apiFetch("/api/me/notifications", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  const body = await response.json() as unknown;
  if (!response.ok || !Array.isArray(body) || !body.every(isNotification)) {
    throw new MonitoringApiError(response.status, "We couldn't load reminders.");
  }
  return body;
}

export async function markNotificationRead(id: number): Promise<void> {
  const response = await apiFetch(`/api/me/notifications/${id}/read`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new MonitoringApiError(response.status, "We couldn't dismiss that reminder.");
  }
}

function isPreference(value: unknown): value is ReminderPreference {
  if (!isRecord(value)) return false;
  return typeof value.enabled === "boolean"
    && typeof value.reminderTime === "string"
    && typeof value.timezone === "string";
}

function isNotification(value: unknown): value is InAppNotification {
  if (!isRecord(value)) return false;
  return typeof value.id === "number"
    && typeof value.type === "string"
    && typeof value.messageKey === "string"
    && typeof value.message === "string"
    && typeof value.createdAt === "string"
    && typeof value.read === "boolean";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
