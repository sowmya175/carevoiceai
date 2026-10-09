let lastAutoReadQuestion: string | null = null;

export function speechSynthesisAvailable(): boolean {
  return typeof window.speechSynthesis?.speak === "function"
    && typeof window.speechSynthesis.cancel === "function"
    && typeof window.SpeechSynthesisUtterance === "function";
}

export function alreadyAutoRead(question: string): boolean {
  return lastAutoReadQuestion === question;
}

export function markAutoRead(question: string): void {
  lastAutoReadQuestion = question;
}

export function resetAutoReadQuestion(): void {
  lastAutoReadQuestion = null;
}

export function preferredEnglishVoice(voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | null {
  return voices.find((voice) => voice.lang.toLowerCase().startsWith("en")) ?? null;
}
