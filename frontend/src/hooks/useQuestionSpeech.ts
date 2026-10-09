import { useCallback, useEffect, useRef, useState } from "react";
import { preferredEnglishVoice, speechSynthesisAvailable } from "../speech/questionSpeech.ts";

export interface QuestionSpeech {
  supported: boolean;
  speaking: boolean;
  notice: string | null;
  speak: (text: string) => void;
  stop: () => void;
}

/**
 * Browser speech for the current CareVoice question.
 * No audio is stored and nothing is sent to a speech service.
 */
export function useQuestionSpeech(): QuestionSpeech {
  const [supported, setSupported] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const generation = useRef(0);

  const stop = useCallback(() => {
    generation.current += 1;
    if (speechSynthesisAvailable()) {
      window.speechSynthesis.cancel();
    }
    setSpeaking(false);
  }, []);

  const speak = useCallback((text: string) => {
    const spoken = text.trim();
    if (!speechSynthesisAvailable() || spoken.length === 0) {
      return;
    }
    window.speechSynthesis.cancel();
    const id = generation.current + 1;
    generation.current = id;
    const utterance = new SpeechSynthesisUtterance(spoken);
    utterance.lang = "en-US";
    utterance.rate = 1;
    utterance.pitch = 1;
    utterance.volume = 1;
    const voice = preferredEnglishVoice(safeVoices());
    if (voice) {
      utterance.voice = voice;
    }
    utterance.onstart = () => {
      if (generation.current === id) setSpeaking(true);
    };
    utterance.onend = () => {
      if (generation.current === id) setSpeaking(false);
    };
    utterance.onerror = (event) => {
      if (generation.current !== id) return;
      setSpeaking(false);
      const reason = "error" in event ? String(event.error) : "";
      if (reason === "interrupted" || reason === "canceled" || reason === "cancelled") return;
      setNotice("Read aloud didn't start. You can try again.");
    };
    setNotice(null);
    setSpeaking(true);
    window.speechSynthesis.speak(utterance);
  }, []);

  useEffect(() => {
    setSupported(speechSynthesisAvailable());
    return () => {
      generation.current += 1;
      if (speechSynthesisAvailable()) window.speechSynthesis.cancel();
    };
  }, []);

  return { supported, speaking, notice, speak, stop };
}

function safeVoices(): SpeechSynthesisVoice[] {
  try {
    const voices = window.speechSynthesis.getVoices?.() ?? [];
    return Array.isArray(voices) ? voices : [];
  } catch {
    return [];
  }
}
