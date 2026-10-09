import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import {
  confirmVoiceTranscript,
  fetchSessionHistory,
  fetchTodayCheckIn,
  MonitoringApiError,
  sendTextMessage,
  startTodayCheckIn,
  transcribeVoice,
} from "../api/monitoringApi.ts";
import { ReminderBanner } from "../components/ReminderBanner.tsx";
import { ReminderSettings } from "../components/ReminderSettings.tsx";
import { PatientHealthConditions } from "../components/PatientHealthConditions.tsx";
import { CheckInComplete } from "../components/CheckInComplete.tsx";
import { CurrentQuestion } from "../components/CurrentQuestion.tsx";
import { QuestionSpeechControls } from "../components/QuestionSpeechControls.tsx";
import { MonitoringStatus } from "../components/MonitoringStatus.tsx";
import { TranscriptCard } from "../components/TranscriptCard.tsx";
import { VoiceRecorder } from "../components/VoiceRecorder.tsx";
import { clearCheckIn, loadCheckIn, saveCheckIn } from "../checkinStorage.ts";
import { dailyStatusLabel, formatCalendarDate } from "../components/historyFormat.ts";
import { formatElapsed } from "../hooks/recordingMime.ts";
import { useQuestionSpeech } from "../hooks/useQuestionSpeech.ts";
import { useVoiceRecorder } from "../hooks/useVoiceRecorder.ts";
import { loadAutoReadQuestions, saveAutoReadQuestions } from "../speech/autoReadPreference.ts";
import type { ClinicalAgentResponse, ConversationTurn, DailyCheckIn, SessionHistory, SessionStatus } from "../types/monitoring.ts";

export function PatientCheckInPage({ patientId, active = true, onPatientChange, onNavigationLockChange, onViewHistory, onViewResponses }: {
  patientId: number;
  active?: boolean;
  onPatientChange?: (id: number | null) => void;
  onNavigationLockChange?: (locked: boolean) => void;
  onViewHistory?: () => void;
  onViewResponses?: (sessionId: number) => void;
}) {
  const recorder = useVoiceRecorder();
  const speech = useQuestionSpeech();
  const prepareRecord = useRef<() => void>(() => {});
  const registerPrepare = useCallback((prepare: () => void) => {
    prepareRecord.current = prepare;
  }, []);
  const [autoRead, setAutoRead] = useState(loadAutoReadQuestions);
  const [today, setToday] = useState<DailyCheckIn | null>(null);
  const [todayReady, setTodayReady] = useState(false);
  const [started, setStarted] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [turns, setTurns] = useState<ConversationTurn[]>([]);
  const [currentQuestion, setCurrentQuestion] = useState<string | null>(null);
  const [status, setStatus] = useState<SessionStatus>("IN_PROGRESS");
  const [conversationComplete, setConversationComplete] = useState(false);
  const [busy, setBusy] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [showText, setShowText] = useState(false);
  const [textDraft, setTextDraft] = useState("");
  const [pendingTranscript, setPendingTranscript] = useState<string | null>(null);
  const [editingTranscript, setEditingTranscript] = useState(false);
  const [editDraft, setEditDraft] = useState("");
  const transcribedBlob = useRef<Blob | null>(null);

  const navigationLocked = busy || recorder.state === "recording" || recorder.state === "uploading";
  const confirmedText = (editingTranscript ? editDraft : pendingTranscript ?? "").trim();
  useEffect(() => {
    if (!active || conversationComplete || currentQuestion === null) speech.stop();
  }, [active, conversationComplete, currentQuestion, speech.stop]);
  useEffect(() => { onPatientChange?.(patientId); }, [patientId, onPatientChange]);
  useEffect(() => { onNavigationLockChange?.(navigationLocked); }, [navigationLocked, onNavigationLockChange]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const status = await fetchTodayCheckIn();
        if (cancelled) return;
        setToday(status);
        const saved = loadCheckIn();
        const sameSession = status.status === "IN_PROGRESS"
          && status.sessionId !== null
          && saved?.sessionId === status.sessionId;
        if (saved && !sameSession) clearCheckIn();
        if (!sameSession || status.sessionId === null) return;
        const history = await fetchSessionHistory(status.sessionId);
        if (cancelled) return;
        if (history.patientId !== patientId || history.sessionId !== status.sessionId) {
          clearCheckIn();
          return;
        }
        saveCheckIn({ sessionId: history.sessionId });
        setSessionId(history.sessionId);
        setStatus(history.status);
        setConversationComplete(history.conversationComplete);
        setCurrentQuestion(history.conversationComplete ? null : history.currentQuestion);
        setTurns(turnsFromHistory(history));
        setStarted(true);
      } catch {
        if (!cancelled) setPageError("We couldn't check today's check-in. Please try again.");
      } finally {
        if (!cancelled) setTodayReady(true);
      }
    })();
    return () => { cancelled = true; };
  }, [patientId]);

  useEffect(() => {
    if (!started || sessionId === null) {
      return;
    }
    saveCheckIn({ sessionId });
  }, [started, sessionId]);

  useEffect(() => {
    if (started && !recorder.supported) {
      setShowText(true);
    }
  }, [started, recorder.supported]);

  useEffect(() => {
    if (sessionId === null || pendingTranscript !== null || recorder.state !== "recorded" || recorder.blob === null) {
      return;
    }
    if (transcribedBlob.current === recorder.blob) {
      return;
    }
    const audio = recorder.blob;
    transcribedBlob.current = audio;
    void transcribe(audio);
  }, [sessionId, pendingTranscript, recorder.state, recorder.blob]);

  async function startCheckIn() {
    setPageError(null);
    setBusy(true);
    try {
      const session = await startTodayCheckIn();
      const question = session.nextQuestion;
      setSessionId(session.sessionId);
      setStatus(session.status);
      setConversationComplete(false);
      setCurrentQuestion(question);
      setTurns(question ? [{ id: nextTurnId(), speaker: "carevoice", text: question }] : []);
      setTextDraft("");
      setStarted(true);
      discardTranscript();
    } catch (error) {
      setPageError(error instanceof MonitoringApiError
        ? error.message
        : "We couldn't start your check-in. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function transcribe(audio: Blob) {
    if (sessionId === null || recorder.state === "uploading") {
      return;
    }
    setPageError(null);
    recorder.beginUpload();
    try {
      const transcript = await transcribeVoice(sessionId, audio);
      setPendingTranscript(transcript);
      setEditDraft(transcript);
      setEditingTranscript(false);
      recorder.endUpload(true);
    } catch (error) {
      recorder.endUpload(false);
      setPageError(error instanceof Error ? error.message : "Something went wrong. Please try again or use text input.");
    }
  }

  async function confirmTranscript() {
    if (sessionId === null || confirmedText.length === 0 || busy) {
      return;
    }
    setBusy(true);
    setPageError(null);
    try {
      const response = await confirmVoiceTranscript(sessionId, confirmedText);
      applyAnswer(confirmedText, response, true);
      discardTranscript();
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "We couldn't send that answer. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  function discardTranscript() {
    transcribedBlob.current = null;
    setPendingTranscript(null);
    setEditingTranscript(false);
    setEditDraft("");
    recorder.clearRecording();
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

  const statusMessage = recorder.state === "recording"
    ? `Recording... ${formatElapsed(recorder.elapsedSeconds)}`
    : recorder.state === "uploading"
      ? "Transcribing your answer..."
      : busy
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
        <>
          <PatientHealthConditions />
          <ReminderBanner active={active} today={today} busy={busy} onOpen={() => void startCheckIn()} />
          <TodayCard today={today} ready={todayReady} busy={busy} onOpen={() => void startCheckIn()}
            onViewResponses={today?.sessionId != null && onViewResponses ? () => onViewResponses(today.sessionId!) : undefined} />
          {todayReady ? <ReminderSettings active={active} /> : null}
        </>
      ) : (
        <>
          <p className="response-progress" aria-live="polite">{answeredCount} {answeredCount === 1 ? "response" : "responses"} saved{conversationComplete ? " · Check-in complete" : " · Check-in in progress"}</p>
          <CurrentQuestion question={conversationComplete ? null : currentQuestion}>
            {currentQuestion ? <QuestionSpeechControls
              question={currentQuestion}
              active={active && !conversationComplete}
              recording={recorder.state === "recording"}
              autoRead={autoRead}
              onAutoReadChange={(enabled) => { setAutoRead(enabled); saveAutoReadQuestions(enabled); }}
              speech={speech}
              onPrepareRecord={registerPrepare}
            /> : null}
          </CurrentQuestion>
          <TranscriptCard turns={turns} />
          <MonitoringStatus message={statusMessage} />
          {conversationComplete ? (
            <>
              <CheckInComplete status={status} onViewHistory={onViewHistory}
                onViewResponses={sessionId !== null && onViewResponses ? () => onViewResponses(sessionId) : undefined} />
            </>
          ) : (
            <>
              {pendingTranscript === null ? (
                <VoiceRecorder recorder={recorder} beforeStart={() => prepareRecord.current()} />
              ) : (
                <section className="recorder heard-review" aria-label="Review what we heard">
                  <p className="heard-label">We heard:</p>
                  {editingTranscript ? (
                    <textarea
                      aria-label="Edit transcript"
                      value={editDraft}
                      onChange={(event) => setEditDraft(event.target.value)}
                      rows={3}
                    />
                  ) : (
                    <p className="heard-text">{`"${pendingTranscript}"`}</p>
                  )}
                  <div className="recorder-actions">
                    <button type="button" className="primary" onClick={() => void confirmTranscript()} disabled={busy || confirmedText.length === 0}>
                      Use this answer
                    </button>
                    <button type="button" className="secondary" onClick={() => setEditingTranscript(true)} disabled={busy || editingTranscript}>
                      Edit
                    </button>
                    <button type="button" className="secondary" onClick={discardTranscript} disabled={busy}>
                      Record again
                    </button>
                  </div>
                </section>
              )}
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

function TodayCard({ today, ready, busy, onOpen, onViewResponses }: {
  today: DailyCheckIn | null;
  ready: boolean;
  busy: boolean;
  onOpen: () => void;
  onViewResponses?: () => void;
}) {
  if (!ready || today === null) return <p role="status">Checking today's check-in…</p>;
  const date = formatCalendarDate(today.previousDaySession ? today.currentDate : today.checkInDate);
  const previous = today.previousDaySession;
  const finished = today.status === "COMPLETED" || today.status === "READY_FOR_REVIEW";
  const action = previous
    ? "Continue previous check-in"
    : today.status === "IN_PROGRESS"
      ? "Continue Check-In"
      : "Start Check-In";
  return (
    <section className="history-card daily-status" aria-label="Today's check-in status">
      <h2>Today's Check-In</h2>
      <p>{date}</p>
      <p>Monitoring Plan: {today.monitoringPlanName ?? "Not assigned"}</p>
      <p>Status: {dailyStatusLabel(today.status)}</p>
      {previous ? <p>Finish your previous check-in before starting today's check-in. The unfinished check-in is {formatCalendarDate(today.checkInDate)}.</p> : null}
      {today.status === "COMPLETED" ? <p>✓ Today's check-in is complete</p> : null}
      {today.status === "READY_FOR_REVIEW" ? <p>Today's check-in was submitted for review.</p> : null}
      {finished ? (
        onViewResponses ? <button type="button" className="secondary" onClick={onViewResponses}>View Today's Responses</button> : null
      ) : (
        <button type="button" className="primary start" onClick={onOpen} disabled={busy}>{action}</button>
      )}
    </section>
  );
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
