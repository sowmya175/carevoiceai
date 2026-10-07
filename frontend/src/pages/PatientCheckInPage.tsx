import { useEffect, useState, type FormEvent } from "react";
import {
  createDevelopmentPatient,
  fetchSessionHistory,
  MonitoringApiError,
  sendTextMessage,
  sendVoiceMessage,
  startMonitoringSession,
} from "../api/monitoringApi.ts";
import { CheckInComplete } from "../components/CheckInComplete.tsx";
import { CurrentQuestion } from "../components/CurrentQuestion.tsx";
import { MonitoringStatus } from "../components/MonitoringStatus.tsx";
import { TranscriptCard } from "../components/TranscriptCard.tsx";
import { VoiceRecorder } from "../components/VoiceRecorder.tsx";
import { clearCheckIn, knownPatientId, loadCheckIn, saveCheckIn } from "../checkinStorage.ts";
import { formatElapsed } from "../hooks/recordingMime.ts";
import { useVoiceRecorder } from "../hooks/useVoiceRecorder.ts";
import type { ClinicalAgentResponse, ConversationTurn, SessionHistory, SessionStatus } from "../types/monitoring.ts";

export function PatientCheckInPage({ onPatientChange, onNavigationLockChange, onViewHistory, onViewResponses }: {
  onPatientChange?: (id: number | null) => void;
  onNavigationLockChange?: (locked: boolean) => void;
  onViewHistory?: () => void;
  onViewResponses?: (sessionId: number) => void;
} = {}) {
  const recorder = useVoiceRecorder();
  const [started, setStarted] = useState(false);
  const [patientId, setPatientId] = useState<number | null>(knownPatientId);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [turns, setTurns] = useState<ConversationTurn[]>([]);
  const [currentQuestion, setCurrentQuestion] = useState<string | null>(null);
  const [status, setStatus] = useState<SessionStatus>("IN_PROGRESS");
  const [conversationComplete, setConversationComplete] = useState(false);
  const [busy, setBusy] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [showText, setShowText] = useState(false);
  const [textDraft, setTextDraft] = useState("");

  const navigationLocked = busy || recorder.state === "recording" || recorder.state === "uploading";
  useEffect(() => { onPatientChange?.(patientId); }, [patientId, onPatientChange]);
  useEffect(() => { onNavigationLockChange?.(navigationLocked); }, [navigationLocked, onNavigationLockChange]);

  useEffect(() => {
    const saved = loadCheckIn();
    if (!saved) {
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const history = await fetchSessionHistory(saved.sessionId);
        if (cancelled) {
          return;
        }
        if (history.patientId !== saved.patientId) {
          clearCheckIn();
          return;
        }
        // Remove any legacy stored conversation fields before displaying restored history.
        saveCheckIn({ patientId: history.patientId, sessionId: history.sessionId });
        setPatientId(history.patientId);
        setSessionId(history.sessionId);
        setStatus(history.status);
        setConversationComplete(history.conversationComplete);
        setCurrentQuestion(history.conversationComplete ? null : history.currentQuestion);
        setTurns(turnsFromHistory(history));
        setStarted(true);
      } catch {
        if (!cancelled) {
          clearCheckIn();
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!started || patientId === null || sessionId === null) {
      return;
    }
    saveCheckIn({ patientId, sessionId });
  }, [started, patientId, sessionId]);

  useEffect(() => {
    if (started && !recorder.supported) {
      setShowText(true);
    }
  }, [started, recorder.supported]);

  async function startCheckIn() {
    setPageError(null);
    setBusy(true);
    try {
      const resolvedPatientId = await resolvePatientId(patientId);
      const session = await startMonitoringSession(resolvedPatientId);
      const question = session.nextQuestion;
      setPatientId(resolvedPatientId);
      setSessionId(session.sessionId);
      setStatus(session.status);
      setConversationComplete(false);
      setCurrentQuestion(question);
      setTurns(question ? [{ id: nextTurnId(), speaker: "carevoice", text: question }] : []);
      setTextDraft("");
      setStarted(true);
      recorder.clearRecording();
    } catch (error) {
      setPageError(error instanceof MonitoringApiError
        ? error.message
        : "We couldn't start your check-in. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function submitVoice(audio: Blob) {
    if (sessionId === null || recorder.state === "uploading") {
      return;
    }
    setPageError(null);
    recorder.beginUpload();
    try {
      const result = await sendVoiceMessage(sessionId, audio);
      applyAnswer(result.transcript, result.agentResponse, true);
      recorder.endUpload(true);
    } catch (error) {
      recorder.endUpload(false);
      setPageError(error instanceof Error ? error.message : "Something went wrong. Please try again or use text input.");
    }
  }

  async function submitText(event: FormEvent) {
    event.preventDefault();
    const message = textDraft.trim();
    if (sessionId === null || message.length === 0 || busy) {
      return;
    }
    setBusy(true);
    setPageError(null);
    try {
      const response = await sendTextMessage(sessionId, message);
      applyAnswer(message, response, false);
      setTextDraft("");
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "We couldn't send that answer. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  function applyAnswer(patientText: string, response: ClinicalAgentResponse, heard: boolean) {
    setTurns((current) => {
      const next = [...current, { id: nextTurnId(), speaker: "patient" as const, text: patientText, heard }];
      if (!response.conversationComplete && response.nextQuestion) {
        next.push({ id: nextTurnId(), speaker: "carevoice", text: response.nextQuestion });
      }
      return next;
    });
    setCurrentQuestion(response.conversationComplete ? null : response.nextQuestion);
    setStatus(response.status);
    setConversationComplete(response.conversationComplete);
  }

  function startAnother() {
    clearCheckIn();
    setStarted(false);
    setSessionId(null);
    setTurns([]);
    setCurrentQuestion(null);
    setConversationComplete(false);
    setStatus("IN_PROGRESS");
    setPageError(null);
    setTextDraft("");
    recorder.clearRecording();
  }

  const statusMessage = recorder.state === "recording"
    ? `Recording... ${formatElapsed(recorder.elapsedSeconds)}`
    : recorder.state === "uploading" || busy
      ? "Processing your response..."
      : null;
  const answeredCount = turns.filter((turn) => turn.speaker === "patient").length;

  return (
    <main className="check-in">
      <header>
        <p className="eyebrow">A moment for you</p>
        <h1>Today's Check-In</h1>
        <p className="subtitle">Share how you've been feeling, in your own words.</p>
        {onViewHistory && <button type="button" className="secondary history-link" onClick={onViewHistory} disabled={navigationLocked}>View History</button>}
      </header>
      {pageError ? <p className="error" role="alert">{pageError}</p> : null}
      {!started ? (
        <button type="button" className="primary start" onClick={() => void startCheckIn()} disabled={busy}>
          Start Today's Check-In
        </button>
      ) : (
        <>
          <p className="response-progress" aria-live="polite">{answeredCount} {answeredCount === 1 ? "response" : "responses"} saved{conversationComplete ? " · Check-in complete" : " · Check-in in progress"}</p>
          <CurrentQuestion question={conversationComplete ? null : currentQuestion} />
          <TranscriptCard turns={turns} />
          <MonitoringStatus message={statusMessage} />
          {conversationComplete ? (
            <>
              <CheckInComplete status={status} onViewHistory={onViewHistory}
                onViewResponses={sessionId !== null && onViewResponses ? () => onViewResponses(sessionId) : undefined} />
              <button type="button" className="secondary" onClick={startAnother}>
                Start another check-in
              </button>
            </>
          ) : (
            <>
              <VoiceRecorder recorder={recorder} onSubmit={(audio) => void submitVoice(audio)} />
              <button type="button" className="text-toggle" onClick={() => setShowText((open) => !open)}>
                Use text instead
              </button>
              {showText ? (
                <form className="text-fallback" onSubmit={(event) => void submitText(event)}>
                  <label htmlFor="text-answer">Type your answer</label>
                  <textarea
                    id="text-answer"
                    value={textDraft}
                    onChange={(event) => setTextDraft(event.target.value)}
                    rows={4}
                  />
                  <button type="submit" className="secondary" disabled={busy || textDraft.trim().length === 0}>
                    Send answer
                  </button>
                </form>
              ) : null}
            </>
          )}
        </>
      )}
    </main>
  );
}

async function resolvePatientId(current: number | null): Promise<number> {
  if (current !== null) {
    return current;
  }
  const configured = import.meta.env.VITE_PATIENT_ID;
  if (typeof configured === "string" && configured.trim().length > 0) {
    return Number(configured);
  }
  const saved = loadCheckIn();
  if (saved) {
    return saved.patientId;
  }
  return createDevelopmentPatient();
}

function nextTurnId(): string {
  return `turn-${crypto.randomUUID()}`;
}

function turnsFromHistory(history: SessionHistory): ConversationTurn[] {
  const turns: ConversationTurn[] = [];
  for (const turn of history.turns) {
    if (turn.question) {
      turns.push({ id: nextTurnId(), speaker: "carevoice", text: turn.question });
    }
    turns.push({
      id: nextTurnId(),
      speaker: "patient",
      text: turn.patientResponse,
      heard: turn.inputMode === "VOICE",
    });
  }
  if (!history.conversationComplete && history.currentQuestion) {
    turns.push({ id: nextTurnId(), speaker: "carevoice", text: history.currentQuestion });
  }
  return turns;
}
