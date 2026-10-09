import { useEffect, useRef, useState } from "react";
import { fetchLongitudinalSummary, MonitoringApiError } from "../api/monitoringApi.ts";
import { HistoryTrends } from "../components/HistoryTrends.tsx";
import { SessionHistoryDetail } from "../components/SessionHistoryDetail.tsx";
import { formatCalendarDate, formatDate, statusLabel } from "../components/historyFormat.ts";
import type { PatientLongitudinalResponse } from "../types/longitudinal.ts";

export function PatientHistoryPage({ patientId, initialSessionId = null, onCheckIn }: {
  patientId: number | null; initialSessionId?: number | null; onCheckIn: () => void;
}) {
  const [data, setData] = useState<PatientLongitudinalResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState<number | null>(initialSessionId);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { if (selected === null) heading.current?.focus(); }, [selected]);
  useEffect(() => {
    if (patientId === null || selected !== null) return;
    const controller = new AbortController();
    setData(null); setError(null);
    void fetchLongitudinalSummary(patientId, controller.signal).then((result) => {
      if (!controller.signal.aborted) setData(result);
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setError(error instanceof MonitoringApiError ? error.message
        : "We couldn't load your health history. Please check your connection and try again.");
    });
    return () => controller.abort();
  }, [patientId, selected, attempt]);

  if (selected !== null && patientId !== null) return <SessionHistoryDetail key={`${patientId}-${selected}`}
    patientId={patientId} sessionId={selected} onBack={() => setSelected(null)} />;

  return <main className="history-page">
    <header className="history-header"><p className="eyebrow">Your check-ins, together</p>
      <h1 ref={heading} tabIndex={-1}>Health History</h1>
      <p className="subtitle">A record of what you've shared, one check-in at a time.</p>
    </header>
    {patientId === null || data?.sessionCount === 0 ? <section className="history-card empty-history">
      <h2>Your history starts here</h2><p>No completed check-ins yet. Once you finish a daily check-in, your history will appear here.</p>
      <button type="button" className="primary" onClick={onCheckIn}>Go to Today's Check-In</button>
    </section> : error ? <section className="history-card"><p role="alert">{error}</p>
      <div className="button-row"><button type="button" className="primary" onClick={() => setAttempt((value) => value + 1)}>Try again</button>
        <button type="button" className="secondary" onClick={onCheckIn}>Go to Today's Check-In</button></div>
    </section> : !data ? <p className="history-card" role="status">Loading your health history…</p> : <>
      <section className="history-overview" aria-label="History overview">
        <p><strong>{data.sessionCount}</strong> check-in{data.sessionCount === 1 ? "" : "s"}</p>
        <div><p>Last check-in: <strong>{formatDate(data.latestCheckInAt)}</strong></p>
          <p className="muted small">{formatDate(data.windowStart)} – {formatDate(data.windowEnd)} · Up to 30 completed check-ins</p></div>
      </section>
      <HistoryTrends data={data} />
      <section className="history-card" aria-label="Recent check-ins"><h2>Recent check-ins</h2>
        <ul className="session-list">{data.sessions.toReversed().map((session) => {
          const when = session.checkInDate ? formatCalendarDate(session.checkInDate) : formatDate(session.startedAt, true);
          return <li key={session.sessionId}>
          <div><h3><time dateTime={session.checkInDate ?? session.startedAt}>{when}</time></h3>
            <p className="muted">{session.monitoringPlanName ? `${session.monitoringPlanName} · ` : null}<span className="session-status">{statusLabel(session.status)}</span> · {session.turnCount} answered {session.turnCount === 1 ? "turn" : "turns"}</p></div>
          <button type="button" className="secondary" aria-label={`View details for ${when}, check-in ${session.sessionId}`}
            onClick={() => setSelected(session.sessionId)}>View details</button>
        </li>;
        })}</ul>
      </section>
    </>}
  </main>;
}
