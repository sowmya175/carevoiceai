import { PatientCheckInPage } from "./pages/PatientCheckInPage.tsx";
import { PatientHistoryPage } from "./pages/PatientHistoryPage.tsx";
import { knownPatientId } from "./checkinStorage.ts";
import { useState } from "react";

export default function App() {
  const [page, setPage] = useState<"check-in" | "history">("check-in");
  const [patientId, setPatientId] = useState<number | null>(knownPatientId);
  const [detailSession, setDetailSession] = useState<number | null>(null);
  const [navigationLocked, setNavigationLocked] = useState(false);
  function showHistory(sessionId: number | null = null) {
    setDetailSession(sessionId); setPage("history");
  }
  return <div className="app-shell">
    <header className="app-header"><div className="brand"><span className="brand-mark" aria-hidden="true">+</span>
      <span>CareVoice<small>Your daily check-in companion</small></span></div>
      <nav aria-label="Patient navigation">
        <button type="button" aria-current={page === "check-in" ? "page" : undefined}
          onClick={() => setPage("check-in")}>Today's Check-In</button>
        <button type="button" aria-current={page === "history" ? "page" : undefined}
          disabled={navigationLocked} onClick={() => showHistory()}>History</button>
      </nav>
    </header>
    <div hidden={page !== "check-in"}>
      <PatientCheckInPage onPatientChange={setPatientId} onNavigationLockChange={setNavigationLocked}
        onViewHistory={() => showHistory()} onViewResponses={(id) => showHistory(id)} />
    </div>
    {page === "history" && <PatientHistoryPage key={`${patientId}-${detailSession}`} patientId={patientId}
      initialSessionId={detailSession} onCheckIn={() => setPage("check-in")} />}
  </div>;
}
