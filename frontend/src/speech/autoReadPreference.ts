const AUTO_READ_KEY = "carevoice.preferences.autoReadQuestions";

/** Patient presentation preference only. Never store questions, answers, or identity here. */
export function loadAutoReadQuestions(): boolean {
  try {
    return localStorage.getItem(AUTO_READ_KEY) === "true";
  } catch {
    return false;
  }
}

export function saveAutoReadQuestions(enabled: boolean): void {
  localStorage.setItem(AUTO_READ_KEY, enabled ? "true" : "false");
}

export function autoReadPreferenceKey(): string {
  return AUTO_READ_KEY;
}
