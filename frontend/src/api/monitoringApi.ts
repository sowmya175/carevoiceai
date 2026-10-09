import type { ClinicalAgentResponse, DailyCheckIn, SessionHistory, SessionStart } from "../types/monitoring.ts";
import type { PatientLongitudinalResponse } from "../types/longitudinal.ts";
import { isLongitudinalResponse } from "./longitudinalResponse.ts";

export class MonitoringApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "MonitoringApiError";
    this.status = status;
  }
}

export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  const value = typeof configured === "string" && configured.trim().length > 0
    ? configured.trim()
    : "http://localhost:8080";
  return value.replace(/\/$/, "");
}

/**
 * CSRF for this React client:
 * The session cookie (JSESSIONID) is HttpOnly, so JavaScript never reads it.
 * Mutating requests first GET /api/auth/csrf with credentials. That response
 * JSON contains the header name and token stored in the server session.
 * The token is kept in memory and sent as that header. It is not written to
 * localStorage or document.cookie. Login rotates the token, so the memory
 * copy is dropped after login and logout and loaded again on the next POST.
 */
let csrfToken: { headerName: string; token: string } | null = null;

export function resetCsrf(): void {
  csrfToken = null;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = headerRecord(init.headers);
  const method = (init.method ?? "GET").toUpperCase();
  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    const csrf = await loadCsrf();
    headers[csrf.headerName] = csrf.token;
  }
  return fetch(`${apiBaseUrl()}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });
}

async function loadCsrf(): Promise<{ headerName: string; token: string }> {
  if (csrfToken) {
    return csrfToken;
  }
  const response = await fetch(`${apiBaseUrl()}/api/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  const body = await readJson(response);
  if (!response.ok || !isRecord(body) || typeof body.headerName !== "string" || typeof body.token !== "string") {
    throw new MonitoringApiError(response.status, "We couldn't start a secure session. Please try again.");
  }
  csrfToken = { headerName: body.headerName, token: body.token };
  return csrfToken;
}

function headerRecord(init?: HeadersInit): Record<string, string> {
  if (!init) {
    return {};
  }
  if (init instanceof Headers) {
    const headers: Record<string, string> = {};
    init.forEach((value, key) => {
      headers[key] = value;
    });
    return headers;
  }
  if (Array.isArray(init)) {
    return Object.fromEntries(init);
  }
  return { ...init };
}

export function messageForFailedResponse(status: number, backendMessage?: string): string {
  if (status === 422) {
    return "We couldn't understand the recording. Please try again.";
  }
  if (status === 502 || status === 404) {
    return "Voice processing is temporarily unavailable. Please try again or use text input.";
  }
  if (status === 400 && isSafeBackendMessage(backendMessage)) {
    return backendMessage;
  }
  return "Something went wrong. Please try again or use text input.";
}

export async function fetchTodayCheckIn(): Promise<DailyCheckIn> {
  const response = await apiFetch("/api/me/check-in/today", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  const body = await readJson(response);
  if (!response.ok || !isDailyCheckIn(body)) {
    throw new MonitoringApiError(response.status, "We couldn't check today's check-in. Please try again.");
  }
  return body;
}

export async function startTodayCheckIn(): Promise<SessionStart> {
  const response = await apiFetch("/api/me/check-in/today", {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  const body = await readJson(response);
  if (!response.ok || !isSessionStart(body)) {
    throw new MonitoringApiError(response.status, "We couldn't start your check-in. Please try again.");
  }
  return body;
}

export async function transcribeVoice(sessionId: number, audio: Blob): Promise<string> {
  const form = new FormData();
  form.append("audio", new File([audio], fileNameFor(audio.type), { type: audio.type || "application/octet-stream" }));
  const response = await apiFetch(`/api/monitoring/sessions/${sessionId}/voice/transcribe`, {
    method: "POST",
    headers: { Accept: "application/json" },
    body: form,
  });
  const body = await readJson(response);
  if (!response.ok || !isRecord(body) || typeof body.transcript !== "string" || body.transcript.trim().length === 0) {
    const backendMessage = isRecord(body) && typeof body.error === "string" ? body.error : undefined;
    throw new MonitoringApiError(response.status, messageForFailedResponse(response.status, backendMessage));
  }
  return body.transcript;
}

export async function fetchSessionHistory(sessionId: number, signal?: AbortSignal): Promise<SessionHistory> {
  const response = await apiFetch(`/api/monitoring/sessions/${sessionId}/history`, {
    headers: { Accept: "application/json" },
    cache: "no-store",
    signal,
  });
  const body = await readJson(response);
  if (!response.ok || !isSessionHistory(body)) {
    throw new MonitoringApiError(response.status, "We couldn't restore your check-in. Please start again.");
  }
  return body;
}

export async function fetchLongitudinalSummary(patientId: number, signal?: AbortSignal): Promise<PatientLongitudinalResponse> {
  const response = await apiFetch(`/api/patients/${patientId}/longitudinal-summary?limit=30`, {
    headers: { Accept: "application/json" }, cache: "no-store", signal,
  });
  const body = await readJson(response);
  if (response.status === 404) {
    throw new MonitoringApiError(404, "We couldn't find your patient history. Please return to Today's Check-In to reconnect.");
  }
  if (!response.ok || !isLongitudinalResponse(body) || body.patientId !== patientId) {
    throw new MonitoringApiError(response.status, "We couldn't load your health history. Please try again.");
  }
  return body;
}

export async function confirmVoiceTranscript(sessionId: number, transcript: string): Promise<ClinicalAgentResponse> {
  return postMessage(sessionId, { message: transcript, inputMode: "VOICE" });
}

export async function sendTextMessage(sessionId: number, message: string): Promise<ClinicalAgentResponse> {
  return postMessage(sessionId, { message });
}

async function postMessage(
  sessionId: number,
  payload: { message: string; inputMode?: "VOICE" },
): Promise<ClinicalAgentResponse> {
  const response = await apiFetch(`/api/monitoring/sessions/${sessionId}/messages`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const body = await readJson(response);
  if (!response.ok || !isAgentResponse(body)) {
    const backendMessage = isRecord(body) && typeof body.error === "string" ? body.error : undefined;
    throw new MonitoringApiError(response.status, textFallbackMessage(response.status, backendMessage));
  }
  return body;
}

function textFallbackMessage(status: number, backendMessage?: string): string {
  if (status === 400 && isSafeBackendMessage(backendMessage)) {
    return backendMessage;
  }
  return "We couldn't send that answer. Please try again.";
}

function fileNameFor(mimeType: string): string {
  if (mimeType.includes("mp4") || mimeType.includes("m4a")) {
    return "answer.m4a";
  }
  if (mimeType.includes("ogg")) {
    return "answer.ogg";
  }
  if (mimeType.includes("wav")) {
    return "answer.wav";
  }
  if (mimeType.includes("mpeg") || mimeType.includes("mp3")) {
    return "answer.mp3";
  }
  return "answer.webm";
}

function isSafeBackendMessage(message: string | undefined): message is string {
  if (!message) {
    return false;
  }
  const compact = message.trim();
  return compact.length > 0
    && compact.length <= 240
    && !compact.includes("\n")
    && !/exception|stack trace|gemini|api[_ ]?key/i.test(compact);
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isSessionStart(value: unknown): value is SessionStart {
  return isRecord(value) && typeof value.sessionId === "number";
}

function isDailyCheckIn(value: unknown): value is DailyCheckIn {
  if (!isRecord(value)) return false;
  const status = value.status;
  return typeof value.checkInDate === "string"
    && typeof value.currentDate === "string"
    && typeof value.timezone === "string"
    && (status === "NOT_STARTED" || status === "IN_PROGRESS" || status === "COMPLETED" || status === "READY_FOR_REVIEW")
    && (value.sessionId === null || typeof value.sessionId === "number")
    && (value.monitoringPlanName === null || typeof value.monitoringPlanName === "string")
    && typeof value.previousDaySession === "boolean";
}

function isAgentResponse(value: unknown): value is ClinicalAgentResponse {
  return isRecord(value)
    && typeof value.sessionId === "number"
    && typeof value.conversationComplete === "boolean"
    && typeof value.status === "string";
}

function isSessionHistory(value: unknown): value is SessionHistory {
  return isRecord(value)
    && typeof value.sessionId === "number"
    && typeof value.patientId === "number"
    && typeof value.status === "string"
    && typeof value.conversationComplete === "boolean"
    && typeof value.startedAt === "string" && Number.isFinite(Date.parse(value.startedAt))
    && Array.isArray(value.turns) && value.turns.every((turn) => isRecord(turn)
      && typeof turn.sequenceNumber === "number" && typeof turn.patientResponse === "string"
      && (turn.question === null || typeof turn.question === "string")
      && typeof turn.timestamp === "string" && Number.isFinite(Date.parse(turn.timestamp)));
}

