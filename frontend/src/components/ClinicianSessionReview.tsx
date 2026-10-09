import { useEffect, useState } from "react";
import { fetchClinicianSession, type ClinicianSession, type ClinicianTurn } from "../api/clinicianApi.ts";
import { formatCalendarDate, formatDate, dailyStatusLabel } from "./historyFormat.ts";
import { monitoringFlagLabel } from "../pages/clinicianList.ts";

export function ClinicianSessionReview({ sessionId, onBack }: { sessionId: number; onBack: () => void }) {
  const [session, setSession] = useState<ClinicianSession | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSession(null);
    setError(null);
    void fetchClinicianSession(sessionId).then((result) => {
      if (!cancelled) setSession(result);
    }).catch(() => {
      if (!cancelled) setError("Unable to load this check-in.");
    });
    return () => { cancelled = true; };
  }, [sessionId]);

  const flag = monitoringFlagLabel(session?.monitoringFlag);
  return <main className="history-page detail-page">
    <button type="button" className="secondary" onClick={onBack}>Back to patient</button>
    {error ? <p role="alert">{error}</p> : null}
    {session === null && !error ? <p role="status">Loading session...</p> : null}
    {session ? <>
      <header className="history-header">
        <p className="eyebrow">Session review</p>
        <h1>{session.checkInDate ? formatCalendarDate(session.checkInDate) : formatDate(session.startedAt, true)}</h1>
        <p>Status: {dailyStatusLabel(session.status)}</p>
        {session.monitoringPlanName ? <p>Plan snapshot: {session.monitoringPlanName}</p> : null}
        {flag ? <p>{flag}</p> : null}
        {session.escalationReason ? <p>Escalation reason: {session.escalationReason}</p> : null}
        <p className="muted small">CareVoice monitoring flags are decision-support signals and are not a diagnosis.</p>
      </header>
      {session.sessionFacts.length > 0 ? <section className="history-card" aria-label="Session facts">
        <h2>Session summary</h2>
        <dl className="fact-list">{session.sessionFacts.map((fact) => <div key={fact.label}>
          <dt>{fact.label}</dt><dd>{fact.value}</dd>
        </div>)}</dl>
      </section> : null}
      <ol className="response-timeline" aria-label="Questions and answers">{session.turns.map((turn) =>
        <Turn key={turn.sequence} turn={turn} />)}</ol>
    </> : null}
  </main>;
}

function Turn({ turn }: { turn: ClinicianTurn }) {
  const answered = turn.inputMode === "VOICE" ? "Patient — Voice" : "Patient — Text";
  const note = turn.clinicalNote;
  const flag = monitoringFlagLabel(note?.monitoringFlag);
  return <li className="history-card">
    <h2>Question {turn.sequence}</h2>
    {turn.question ? <div className="timeline-question"><h3>CareVoice</h3><p className="preserve-text">{turn.question}</p></div> : null}
    <div className="timeline-answer"><h3>{answered}</h3><p className="preserve-text">{turn.patientResponse}</p></div>
    <time className="muted small" dateTime={turn.createdAt}>{formatDate(turn.createdAt, true)}</time>
    {note?.noteText ? <div><h3>{note.noteLabel}</h3><p className="preserve-text">{note.noteText}</p></div>
      : <p>CareVoice note not available.</p>}
    {flag ? <p>Monitoring flag: {flag}</p> : null}
    {note?.escalationReason ? <p>Escalation reason: {note.escalationReason}</p> : null}
    {note && note.facts.length > 0 ? <dl className="fact-list">{note.facts.map((fact) => <div key={fact.label}>
      <dt>{fact.label}</dt><dd>{fact.value}</dd>
    </div>)}</dl> : null}
  </li>;
}
