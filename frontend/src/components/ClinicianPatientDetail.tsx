import { useEffect, useState } from "react";
import {
  assignMonitoringPlan,
  fetchMonitoringPlans,
  type MonitoringPlanChoice,
} from "../api/authApi.ts";
import {
  fetchClinicianHistory,
  fetchClinicianOverview,
  fetchClinicianTrends,
  type ClinicianHistorySession,
  type ClinicianOverview,
} from "../api/clinicianApi.ts";
import { ClinicianConditions } from "./ClinicianConditions.tsx";
import { ClinicianPlanProposal } from "./ClinicianPlanProposal.tsx";
import { HistoryTrends } from "./HistoryTrends.tsx";
import { formatCalendarDate, formatDate, dailyStatusLabel } from "./historyFormat.ts";
import { monitoringFlagLabel, reminderLabel } from "../pages/clinicianList.ts";
import type { PatientLongitudinalResponse } from "../types/longitudinal.ts";

export function ClinicianPatientDetail({ patientId, onBack, onOpenSession, onPlanChanged }: {
  patientId: number;
  onBack: () => void;
  onOpenSession: (sessionId: number) => void;
  onPlanChanged: (name: string) => void;
}) {
  const [overview, setOverview] = useState<ClinicianOverview | null>(null);
  const [history, setHistory] = useState<ClinicianHistorySession[] | null>(null);
  const [trends, setTrends] = useState<PatientLongitudinalResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [plans, setPlans] = useState<MonitoringPlanChoice[] | null>(null);
  const [choosing, setChoosing] = useState(false);
  const [choiceId, setChoiceId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [assignError, setAssignError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setOverview(null);
    setHistory(null);
    setTrends(null);
    setError(null);
    void Promise.all([
      fetchClinicianOverview(patientId),
      fetchClinicianHistory(patientId),
      fetchClinicianTrends(patientId),
    ]).then(([nextOverview, nextHistory, nextTrends]) => {
      if (cancelled) return;
      setOverview(nextOverview);
      setHistory(nextHistory);
      setTrends(nextTrends);
    }).catch(() => {
      if (!cancelled) setError("Unable to load patient details.");
    });
    return () => { cancelled = true; };
  }, [patientId]);

  async function openPlans() {
    setChoosing(true);
    setChoiceId(null);
    setAssignError(null);
    if (plans !== null) return;
    try {
      setPlans(await fetchMonitoringPlans());
    } catch {
      setError("Unable to load patient details.");
    }
  }

  async function confirmPlan() {
    const chosen = plans?.find((plan) => plan.id === choiceId);
    if (choiceId === null || saving || chosen == null || chosen.name === overview?.activeMonitoringPlanName) return;
    setSaving(true);
    setAssignError(null);
    let assignedName: string | null = null;
    try {
      const assigned = await assignMonitoringPlan(patientId, choiceId);
      assignedName = assigned.name;
      onPlanChanged(assigned.name);
      setOverview((current) => current ? { ...current, activeMonitoringPlanName: assigned.name } : current);
      setChoosing(false);
    } catch {
      setAssignError("Unable to assign monitoring plan. Please try again.");
    } finally {
      setSaving(false);
    }
    if (assignedName === null) return;
    try {
      setOverview(await fetchClinicianOverview(patientId));
    } catch {
      setOverview((current) => current ? { ...current, activeMonitoringPlanName: assignedName } : current);
    }
  }

  function refreshOverview() {
    void fetchClinicianOverview(patientId).then(setOverview).catch(() => {
      setError("Unable to load patient details.");
    });
  }

  const flag = monitoringFlagLabel(overview?.monitoringFlag);
  const noHistory = history !== null && history.length === 0 && trends?.sessionCount === 0;

  return <main className="history-page">
    <button type="button" className="secondary" onClick={onBack}>Back to patients</button>
    {error ? <p role="alert">{error}</p> : null}
    {overview === null && !error ? <p role="status">Loading patient history...</p> : null}
    {overview ? <>
      <header className="history-header">
        <p className="eyebrow">Patient summary</p>
        <h1>{overview.fullName}</h1>
        <p className="muted">CareVoice monitoring flags are decision-support signals and are not a diagnosis.</p>
      </header>
      <div className="clinician-columns">
        <section className="history-card" aria-label="Patient summary">
          <h2>Patient summary</h2>
          {overview.medicalCondition ? <p>Condition: {overview.medicalCondition}</p> : null}
          <p>Patient timezone: {overview.timezone ?? "Unknown"}</p>
          <p>{overview.activeMonitoringPlanName
            ? `Current Monitoring Plan: ${overview.activeMonitoringPlanName}`
            : "No monitoring plan assigned"}</p>
          <p>Daily reminder: {reminderLabel(overview.reminderEnabled, overview.reminderTime)}</p>
          <p>Today's check-in: {dailyStatusLabel(overview.todayStatus)}</p>
          {choosing ? <form className="plan-form" onSubmit={(event) => { event.preventDefault(); void confirmPlan(); }}>
            <fieldset>
              <legend>Available plans</legend>
              {plans === null ? <p role="status">Loading plans…</p> : plans.map((plan) => <label key={plan.id}>
                <input type="radio" name="detail-plan" checked={choiceId === plan.id} onChange={() => { setChoiceId(plan.id); setAssignError(null); }} />
                {plan.name}
              </label>)}
            </fieldset>
            <button
              type="submit"
              disabled={choiceId === null || saving || plans?.find((plan) => plan.id === choiceId)?.name === overview.activeMonitoringPlanName}
            >Confirm assignment</button>
            {saving ? <p role="status">Assigning...</p> : null}
            {assignError ? <p className="error" role="alert">{assignError}</p> : null}
          </form> : <button type="button" onClick={() => void openPlans()}>Change Monitoring Plan</button>}
          <p className="muted small">Changes apply to future check-ins. An active check-in keeps its current plan.</p>
        </section>
        <section className="history-card" aria-label="Today's check-in">
          <h2>Today's check-in</h2>
          <p>{overview.checkInDate ? formatCalendarDate(overview.previousDaySession ? overview.currentDate : overview.checkInDate) : "Not available"}</p>
          <p>Status: {dailyStatusLabel(overview.todayStatus)}</p>
          {overview.sessionPlanName ? <p>Plan snapshot: {overview.sessionPlanName}</p> : null}
          {overview.startedAt ? <p>Started: {formatDate(overview.startedAt, true)}</p> : null}
          {overview.completedAt ? <p>Completed: {formatDate(overview.completedAt, true)}</p> : null}
          {overview.todayStatus === "IN_PROGRESS" ? <p>Questions answered: {overview.questionsAnswered}</p> : null}
          {flag ? <p>{flag}</p> : null}
          {overview.escalationReason ? <p>Escalation reason: {overview.escalationReason}</p> : null}
          {overview.requiresReview ? <p className="review-note">Requires review</p> : null}
        </section>
      </div>
      <ClinicianConditions patientId={patientId} onChanged={refreshOverview} />
      <ClinicianPlanProposal patientId={patientId} onChanged={refreshOverview} />
      <section className="history-card" aria-label="Longitudinal trends">
        <h2>Longitudinal trends</h2>
        {trends === null ? null : trends.sessionCount === 0
          ? <p>Not enough monitoring history yet.</p>
          : <HistoryTrends data={trends} eyebrow="Reported scores" />}
      </section>
      <section className="history-card" aria-label="Check-in history">
        <h2>Check-in history</h2>
        {noHistory ? <p>No completed check-ins yet.</p> : null}
        {history && history.length > 0 ? <ul className="session-list">{history.map((session) => {
          const when = session.checkInDate ? formatCalendarDate(session.checkInDate) : formatDate(session.startedAt, true);
          const sessionFlag = monitoringFlagLabel(session.riskLevel);
          return <li key={session.sessionId}>
            <div>
              <h3><time dateTime={session.checkInDate ?? session.startedAt}>{when}</time></h3>
              <p className="muted">{session.monitoringPlanName ? `${session.monitoringPlanName} · ` : null}{dailyStatusLabel(session.status)} · {session.turnCount} answered {session.turnCount === 1 ? "turn" : "turns"}</p>
              {sessionFlag ? <p>{sessionFlag}</p> : null}
              <p className="muted small">Started: {formatDate(session.startedAt, true)}{session.completedAt ? ` · Completed: ${formatDate(session.completedAt, true)}` : ""}</p>
            </div>
            <button type="button" className="secondary" onClick={() => onOpenSession(session.sessionId)}>View session</button>
          </li>;
        })}</ul> : null}
      </section>
    </> : null}
  </main>;
}
