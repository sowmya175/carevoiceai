import { useEffect, useRef, useState } from "react";
import { fetchSessionHistory } from "../api/monitoringApi.ts";
import type { SessionHistory } from "../types/monitoring.ts";
import { formatDate, statusLabel } from "./historyFormat.ts";

export function SessionHistoryDetail({ patientId, sessionId, onBack }: {
  patientId: number; sessionId: number; onBack: () => void;
}) {
  const [history, setHistory] = useState<SessionHistory | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);
  useEffect(() => {
    const controller = new AbortController();
    setHistory(null); setError(false);
    void fetchSessionHistory(sessionId, controller.signal).then((result) => {
      if (controller.signal.aborted) return;
      if (result.patientId !== patientId || result.sessionId !== sessionId) { setError(true); return; }
      setHistory(result);
    }).catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [patientId, sessionId, attempt]);

  return <main className="history-page detail-page">
    <button type="button" className="secondary" onClick={onBack}>Back to health history</button>
    <header className="history-header"><p className="eyebrow">Your saved responses</p>
      <h1 ref={heading} tabIndex={-1}>Check-in details</h1>
      {history && <p className="subtitle">{formatDate(history.startedAt, true)} · {statusLabel(history.status)}</p>}
    </header>
    {error ? <div className="history-card"><p role="alert">We couldn't load this check-in. Please try again.</p>
      <button type="button" className="secondary" onClick={() => setAttempt((value) => value + 1)}>Try again</button></div>
      : !history ? <p role="status">Loading your responses…</p>
      : history.turns.length === 0 ? <p className="history-card">No responses have been recorded for this check-in yet.</p>
      : <ol className="response-timeline">{history.turns.map((turn) => <li key={turn.sequenceNumber} className="history-card">
        <time className="muted small" dateTime={turn.timestamp}>{formatDate(turn.timestamp, true)}</time>
        {turn.question && <div className="timeline-question"><h2>CareVoice</h2><p className="preserve-text">{turn.question}</p></div>}
        <div className="timeline-answer"><h2>Your answer</h2><p className="preserve-text">{turn.patientResponse}</p></div>
      </li>)}</ol>}
  </main>;
}
