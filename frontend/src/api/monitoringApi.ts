import type { ClinicalAgentResponse, SessionHistory, SessionStart, VoiceMonitoringResponse } from "../types/monitoring.ts";
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

export async function createDevelopmentPatient(): Promise<number> {
  const response = await fetch(`${apiBaseUrl()}/api/patients`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      displayName: "Daily Check-In",
      monitoringPlan: "Daily monitoring",
    }),
  });
  const body = await readJson(response);
  if (!response.ok || !isRecord(body) || typeof body.id !== "number") {
    throw new MonitoringApiError(response.status, "We couldn't start your check-in. Please try again.");
  }
  return body.id;
}

export async function startMonitoringSession(patientId: number): Promise<SessionStart> {
  const response = await fetch(`${apiBaseUrl()}/api/monitoring/patients/${patientId}/sessions`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  const body = await readJson(response);
  if (!response.ok || !isSessionStart(body)) {
    throw new MonitoringApiError(response.status, "We couldn't start your check-in. Please try again.");
  }
  return body;
}

export async function sendVoiceMessage(sessionId: number, audio: Blob): Promise<VoiceMonitoringResponse> {
  const form = new FormData();
  form.append("audio", new File([audio], fileNameFor(audio.type), { type: audio.type || "application/octet-stream" }));
  const response = await fetch(`${apiBaseUrl()}/api/monitoring/sessions/${sessionId}/voice`, {
    method: "POST",
    headers: { Accept: "application/json" },
    body: form,
  });
  const body = await readJson(response);
  if (!response.ok || !isVoiceResponse(body)) {
    const backendMessage = isRecord(body) && typeof body.error === "string" ? body.error : undefined;
    throw new MonitoringApiError(response.status, messageForFailedResponse(response.status, backendMessage));
  }
  return body;
}

export async function fetchSessionHistory(sessionId: number, signal?: AbortSignal): Promise<SessionHistory> {
  const response = await fetch(`${apiBaseUrl()}/api/monitoring/sessions/${sessionId}/history`, {
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
  const response = await fetch(`${apiBaseUrl()}/api/patients/${patientId}/longitudinal-summary?limit=30`, {
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

export async function sendTextMessage(sessionId: number, message: string): Promise<ClinicalAgentResponse> {
  const response = await fetch(`${apiBaseUrl()}/api/monitoring/sessions/${sessionId}/messages`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ message }),
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

function isVoiceResponse(value: unknown): value is VoiceMonitoringResponse {
  return isRecord(value)
    && typeof value.transcript === "string"
    && isAgentResponse(value.agentResponse);
}
