import { useState, type FormEvent } from "react";
import { fetchCurrentUser, loginClinician, loginPatient, registerAccount, type CurrentUser } from "../api/authApi.ts";
import { MonitoringApiError } from "../api/monitoringApi.ts";

type Mode = "choose" | "patient" | "doctor" | "register";

export function AuthScreen({ onAuthenticated }: { onAuthenticated: (user: CurrentUser) => void }) {
  const [mode, setMode] = useState<Mode>("choose");
  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [medicalCondition, setMedicalCondition] = useState("");
  const [timezone, setTimezone] = useState(defaultTimezone);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "register") {
        await registerAccount({ fullName, username, password, medicalCondition, timezone });
        await loginPatient(username, password);
      } else if (mode === "doctor") {
        await loginClinician(username, password);
      } else {
        await loginPatient(username, password);
      }
      const user = await fetchCurrentUser();
      if (!user) throw new MonitoringApiError(401, "Invalid username or password.");
      if (mode === "doctor" && user.role !== "CLINICIAN") {
        throw new MonitoringApiError(401, "Invalid username or password.");
      }
      if (mode !== "doctor" && (user.role !== "PATIENT" || !user.patient)) {
        throw new MonitoringApiError(401, "Invalid username or password.");
      }
      setPassword("");
      onAuthenticated(user);
    } catch (caught) {
      setError(caught instanceof MonitoringApiError ? caught.message : "Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  if (mode === "choose") {
    return <main className="auth-panel">
      <h1>CareVoice</h1>
      <section aria-label="Patient">
        <h2>Patient</h2>
        <div className="button-row">
          <button type="button" className="primary" onClick={() => setMode("patient")}>Patient Login</button>
          <button type="button" className="secondary" onClick={() => setMode("register")}>Create Patient Account</button>
        </div>
      </section>
      <section aria-label="Medical Professional">
        <h2>Medical Professional</h2>
        <div className="button-row">
          <button type="button" className="primary" onClick={() => setMode("doctor")}>Doctor Login</button>
        </div>
      </section>
    </main>;
  }

  const title = mode === "doctor" ? "Doctor login" : mode === "register" ? "Create patient account" : "Patient login";
  return <main className="auth-panel">
    <h1>CareVoice</h1>
    <form aria-label={title} onSubmit={submit}>
      {mode === "register" ? <>
        <label htmlFor="full-name">Full Name</label>
        <input id="full-name" value={fullName} autoComplete="name" required maxLength={120}
          onChange={(event) => setFullName(event.target.value)} />
      </> : null}
      <label htmlFor="username">Username</label>
      <input id="username" value={username} autoComplete="username" required maxLength={32}
        onChange={(event) => setUsername(event.target.value)} />
      <label htmlFor="password">Password</label>
      <input id="password" type="password" value={password} required minLength={12} maxLength={72}
        autoComplete={mode === "register" ? "new-password" : "current-password"}
        onChange={(event) => setPassword(event.target.value)} />
      {mode === "register" ? <>
        <label htmlFor="medical-condition">Medical Condition</label>
        <input id="medical-condition" value={medicalCondition} required maxLength={255}
          onChange={(event) => setMedicalCondition(event.target.value)} />
        <label htmlFor="timezone">Timezone</label>
        <select id="timezone" value={timezone} required onChange={(event) => setTimezone(event.target.value)}>
          {timeZones().map((zone) => <option key={zone} value={zone}>{zone}</option>)}
        </select>
      </> : null}
      {error ? <p role="alert">{error}</p> : null}
      <div className="button-row">
        <button type="submit" className="primary" disabled={busy}>
          {mode === "doctor" ? "Doctor Login" : mode === "register" ? "Create Patient Account" : "Patient Login"}
        </button>
        <button type="button" className="secondary" onClick={() => { setMode("choose"); setError(null); }}>Back</button>
      </div>
    </form>
  </main>;
}

const defaultTimezone = (() => {
  const resolved = Intl.DateTimeFormat().resolvedOptions().timeZone;
  return timeZones().includes(resolved) ? resolved : "UTC";
})();

function timeZones(): string[] {
  const supported = typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : [];
  const zones = [...supported];
  for (const zone of ["UTC", "America/New_York"]) {
    if (!zones.includes(zone)) zones.unshift(zone);
  }
  return zones;
}
