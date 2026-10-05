const STORAGE_KEY = "carevoice.checkin";

export interface SavedCheckIn {
  patientId: number;
  sessionId: number;
}

export function loadCheckIn(): SavedCheckIn | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    if (!isSavedCheckIn(parsed)) {
      return null;
    }
    return { patientId: parsed.patientId, sessionId: parsed.sessionId };
  } catch {
    return null;
  }
}

export function saveCheckIn(value: SavedCheckIn): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    patientId: value.patientId,
    sessionId: value.sessionId,
  }));
}

export function clearCheckIn(): void {
  localStorage.removeItem(STORAGE_KEY);
}

function isSavedCheckIn(value: unknown): value is SavedCheckIn {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return typeof record.patientId === "number" && typeof record.sessionId === "number";
}
