import { useEffect, useState } from "react";
import {
  assignMonitoringPlan,
  fetchClinicianPatients,
  fetchMonitoringPlans,
  type ClinicianPatient,
  type MonitoringPlanChoice,
} from "../api/authApi.ts";
import { MonitoringApiError } from "../api/monitoringApi.ts";
import { ClinicianPatientDetail } from "../components/ClinicianPatientDetail.tsx";
import { ClinicianSessionReview } from "../components/ClinicianSessionReview.tsx";
import { dailyStatusLabel, formatDate } from "../components/historyFormat.ts";
import {
  dashboardCounts,
  monitoringFlagLabel,
  needsReview,
  visiblePatients,
  type StatusFilter,
} from "./clinicianList.ts";

type View =
  | { name: "list" }
  | { name: "patient"; patientId: number }
  | { name: "session"; patientId: number; sessionId: number };

const FILTERS: { id: StatusFilter; label: string }[] = [
  { id: "ALL", label: "All" },
  { id: "NOT_STARTED", label: "Not started" },
  { id: "IN_PROGRESS", label: "In progress" },
  { id: "COMPLETED", label: "Completed" },
  { id: "READY_FOR_REVIEW", label: "Ready for review" },
];

export function ClinicianPatientsPage() {
  const [patients, setPatients] = useState<ClinicianPatient[] | null>(null);
  const [plans, setPlans] = useState<MonitoringPlanChoice[] | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [choiceId, setChoiceId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [assignError, setAssignError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [view, setView] = useState<View>({ name: "list" });

  useEffect(() => {
    if (view.name !== "list") return;
    let cancelled = false;
    setError(null);
    void fetchClinicianPatients().then((result) => {
      if (!cancelled) setPatients(result);
    }).catch((caught: unknown) => {
      if (!cancelled) setError(caught instanceof MonitoringApiError
        ? caught.message
        : "We couldn't load the patient list. Please try again.");
    });
    return () => { cancelled = true; };
  }, [view]);

  async function openPlanSelector(patientId: number) {
    setSelectedId(patientId);
    setChoiceId(null);
    setAssignError(null);
    setError(null);
    if (plans !== null) return;
    try {
      setPlans(await fetchMonitoringPlans());
    } catch (caught: unknown) {
      setError(caught instanceof MonitoringApiError
        ? caught.message
        : "We couldn't load monitoring plans. Please try again.");
    }
  }

  async function confirmAssignment(patientId: number, currentPlanName: string | null) {
    const chosen = plans?.find((plan) => plan.id === choiceId);
    if (choiceId === null || saving || chosen == null || chosen.name === currentPlanName) return;
    setSaving(true);
    setAssignError(null);
    try {
      const assigned = await assignMonitoringPlan(patientId, choiceId);
      setPatients((current) => current?.map((patient) => patient.patientId === patientId
        ? { ...patient, monitoringPlanName: assigned.name }
        : patient) ?? current);
      setSelectedId(null);
      setChoiceId(null);
    } catch {
      setAssignError("Unable to assign monitoring plan. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  if (view.name === "session") {
    return <ClinicianSessionReview sessionId={view.sessionId} onBack={() => setView({ name: "patient", patientId: view.patientId })} />;
  }
  if (view.name === "patient") {
    return <ClinicianPatientDetail
      patientId={view.patientId}
      onBack={() => setView({ name: "list" })}
      onOpenSession={(sessionId) => setView({ name: "session", patientId: view.patientId, sessionId })}
      onPlanChanged={(name) => setPatients((current) => current?.map((patient) => patient.patientId === view.patientId
        ? { ...patient, monitoringPlanName: name }
        : patient) ?? current)}
    />;
  }

  const counts = dashboardCounts(patients ?? []);
  const shown = visiblePatients(patients ?? [], query, filter);

  return <main className="history-page">
    <header className="history-header">
      <h1>Patients</h1>
      <p className="subtitle">Today's overview uses each patient's own timezone.</p>
    </header>
    {error ? <p role="alert">{error}</p> : null}
    {patients === null && !error ? <p role="status">Loading patients…</p> : null}
    {patients ? <section className="overview-cards" aria-label="Today's overview">
      <article><p>Patients</p><strong>{counts.patients}</strong></article>
      <article><p>Not started</p><strong>{counts.notStarted}</strong></article>
      <article><p>In progress</p><strong>{counts.inProgress}</strong></article>
      <article><p>Completed</p><strong>{counts.completed}</strong></article>
      <article><p>Ready for review</p><strong>{counts.readyForReview}</strong></article>
    </section> : null}
    <div className="dashboard-tools">
      <label htmlFor="patient-search">Search patients</label>
      <input id="patient-search" value={query} onChange={(event) => setQuery(event.target.value)} />
      <div className="filter-row" role="group" aria-label="Filter by today's check-in">
        {FILTERS.map((item) => <button
          key={item.id}
          type="button"
          aria-pressed={filter === item.id}
          onClick={() => setFilter(item.id)}
        >{item.label}</button>)}
      </div>
    </div>
    {patients?.length === 0 ? <p>No patients have registered yet.</p> : null}
    {patients && patients.length > 0 && shown.length === 0 ? <p>No patients match this search.</p> : null}
    <ul className="patient-list">
      {shown.map((patient) => {
        const flag = monitoringFlagLabel(patient.latestMonitoringFlag);
        return <li key={patient.patientId}>
          <article aria-labelledby={`patient-${patient.patientId}`}>
            <h2 id={`patient-${patient.patientId}`}>{patient.fullName}</h2>
            {patient.medicalCondition ? <p>Condition: {patient.medicalCondition}</p> : null}
            <p>{patient.monitoringPlanName
              ? `Monitoring Plan: ${patient.monitoringPlanName}`
              : "No monitoring plan assigned"}</p>
            {patient.todayStatus ? <p>Today: {dailyStatusLabel(patient.todayStatus)}</p> : null}
            {needsReview(patient) ? <p className="review-note">Requires review</p> : null}
            {flag ? <p>{flag}</p> : null}
            {patient.latestCheckInAt ? <p>Last check-in: {formatDate(patient.latestCheckInAt, true)}</p> : null}
            <div className="button-row">
              <button type="button" className="primary" onClick={() => setView({ name: "patient", patientId: patient.patientId })}>View Patient</button>
              {selectedId === patient.patientId ? null : <button type="button" onClick={() => void openPlanSelector(patient.patientId)}>
                Change Monitoring Plan
              </button>}
            </div>
            {selectedId === patient.patientId ? <form className="plan-form" onSubmit={(event) => {
              event.preventDefault();
              void confirmAssignment(patient.patientId, patient.monitoringPlanName);
            }}>
              <fieldset>
                <legend>Available plans</legend>
                {plans === null ? <p role="status">Loading plans…</p> : plans.map((plan) => <label key={plan.id}>
                  <input
                    type="radio"
                    name={`plan-${patient.patientId}`}
                    checked={choiceId === plan.id}
                    onChange={() => { setChoiceId(plan.id); setAssignError(null); }}
                  />
                  {plan.name}
                </label>)}
              </fieldset>
              <button
                type="submit"
                disabled={choiceId === null || saving || plans?.find((plan) => plan.id === choiceId)?.name === patient.monitoringPlanName}
              >Confirm assignment</button>
              {saving ? <p role="status">Assigning...</p> : null}
              {assignError ? <p className="error" role="alert">{assignError}</p> : null}
            </form> : null}
          </article>
        </li>;
      })}
    </ul>
  </main>;
}
