import { useEffect, useState } from "react";
import { fetchCurrentUser, fetchMyMonitoringPlan, logout, type CurrentUser } from "./api/authApi.ts";
import { AuthScreen } from "./auth/AuthScreen.tsx";
import { clearCheckIn } from "./checkinStorage.ts";
import { ClinicianPatientsPage } from "./pages/ClinicianPatientsPage.tsx";
import { PatientCheckInPage } from "./pages/PatientCheckInPage.tsx";
import { PatientHistoryPage } from "./pages/PatientHistoryPage.tsx";

export default function App() {
  const [account, setAccount] = useState<CurrentUser | null | undefined>(undefined);
  const [page, setPage] = useState<"check-in" | "history">("check-in");
  const [detailSession, setDetailSession] = useState<number | null>(null);
  const [navigationLocked, setNavigationLocked] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [monitoringPlanName, setMonitoringPlanName] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void fetchCurrentUser().then((user) => {
      if (!cancelled) setAccount(user);
    }).catch(() => {
      if (!cancelled) {
        setSessionError("We couldn't confirm your session. Please try again.");
        setAccount(null);
      }
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (account?.role !== "PATIENT" || account.patient == null) {
      setMonitoringPlanName(null);
      return;
    }
    let cancelled = false;
    void fetchMyMonitoringPlan().then((plan) => {
      if (!cancelled) setMonitoringPlanName(plan.name);
    }).catch(() => {
      if (!cancelled) setMonitoringPlanName(null);
    });
    return () => { cancelled = true; };
  }, [account]);

  function showHistory(sessionId: number | null = null) {
    setDetailSession(sessionId);
    setPage("history");
  }

  async function signOut() {
    await logout();
    clearCheckIn();
    setAccount(null);
    setPage("check-in");
    setDetailSession(null);
  }

  const clinician = account?.role === "CLINICIAN";
  const patient = account?.role === "PATIENT" ? account.patient : null;

  return <div className="app-shell">
    <header className="app-header">
      <div className="brand"><span className="brand-mark" aria-hidden="true">+</span>
        <span><span className="brand-name">{clinician ? "CareVoice Clinical" : "CareVoice"}</span>
          <small>{clinician ? "Clinician view" : "Your daily check-in companion"}</small></span></div>
      {patient ? <div className="account-summary">
        <p>Welcome, {patient.fullName}</p>
        {patient.medicalCondition ? <p>Condition: {patient.medicalCondition}</p> : null}
        {monitoringPlanName ? <p>Monitoring Plan: {monitoringPlanName}</p> : null}
      </div> : null}
      {clinician ? <div className="account-summary"><p>Welcome, Doctor</p></div> : null}
      {patient ? <nav aria-label="Patient navigation">
        <button type="button" aria-current={page === "check-in" ? "page" : undefined}
          onClick={() => setPage("check-in")}>Today's Check-In</button>
        <button type="button" aria-current={page === "history" ? "page" : undefined}
          disabled={navigationLocked} onClick={() => showHistory()}>History</button>
        <button type="button" disabled={navigationLocked} onClick={() => void signOut()}>Sign Out</button>
      </nav> : null}
      {clinician ? <nav aria-label="Clinician navigation">
        <button type="button" aria-current="page">Patients</button>
        <button type="button" onClick={() => void signOut()}>Sign Out</button>
      </nav> : null}
    </header>
    {account === undefined ? <p className="auth-panel" role="status">Checking your session…</p> : null}
    {account === null ? <>
      {sessionError ? <p className="auth-panel" role="alert">{sessionError}</p> : null}
      <AuthScreen onAuthenticated={(user) => { setSessionError(null); setAccount(user); }} />
    </> : null}
    {patient ? <>
      <div hidden={page !== "check-in"}>
        <PatientCheckInPage patientId={patient.id} active={page === "check-in"} onNavigationLockChange={setNavigationLocked}
          onViewHistory={() => showHistory()} onViewResponses={(id) => showHistory(id)} />
      </div>
      {page === "history" ? <PatientHistoryPage key={`${patient.id}-${detailSession}`}
        patientId={patient.id} initialSessionId={detailSession} onCheckIn={() => setPage("check-in")} /> : null}
    </> : null}
    {clinician ? <ClinicianPatientsPage /> : null}
  </div>;
}
