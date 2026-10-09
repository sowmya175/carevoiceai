import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ClinicianPatientsPage } from "./ClinicianPatientsPage.tsx";
import { emptySummary, historySummary, jsonResponse } from "../test/historyFixtures.ts";

const patients = [
  {
    patientId: 7, fullName: "Sarah Miller", medicalCondition: "Post-operative recovery", timezone: "America/New_York",
    monitoringPlanName: "Post-Operative Recovery Demo", todayCheckInDate: "2026-10-08", todayStatus: "READY_FOR_REVIEW",
    todaySessionId: 101, latestMonitoringFlag: "RED", latestCheckInAt: "2026-10-08T14:00:00Z", requiresReview: true,
  },
  {
    patientId: 8, fullName: "John Smith", medicalCondition: "Hypertension", timezone: "America/Chicago",
    monitoringPlanName: "Hypertension Symptom Monitoring Demo", todayCheckInDate: "2026-10-08", todayStatus: "NOT_STARTED",
    todaySessionId: null, latestMonitoringFlag: null, latestCheckInAt: null, requiresReview: false,
  },
  {
    patientId: 9, fullName: "Maria Alvarez", medicalCondition: "General recovery", timezone: "America/Denver",
    monitoringPlanName: "General Daily Wellness", todayCheckInDate: "2026-10-08", todayStatus: "IN_PROGRESS",
    todaySessionId: 88, latestMonitoringFlag: "YELLOW", latestCheckInAt: "2026-10-08T15:00:00Z", requiresReview: false,
  },
  {
    patientId: 10, fullName: "Ada Lovelace", medicalCondition: "Daily wellness", timezone: "UTC",
    monitoringPlanName: "General Daily Wellness", todayCheckInDate: "2026-10-08", todayStatus: "COMPLETED",
    todaySessionId: 90, latestMonitoringFlag: "GREEN", latestCheckInAt: "2026-10-08T12:00:00Z", requiresReview: false,
  },
];

const plans = [
  { id: 1, name: "General Daily Wellness", description: "Demo monitoring plan. Not clinically validated.", conditionLabel: "Daily wellness" },
  { id: 2, name: "Post-Operative Recovery Demo", description: "Demo monitoring plan. Not clinically validated.", conditionLabel: "Post-operative recovery" },
  { id: 3, name: "Hypertension Symptom Monitoring Demo", description: "Demo monitoring plan. Not clinically validated.", conditionLabel: "Hypertension symptoms" },
  { id: 4, name: "Diabetes Daily Wellness Demo", description: "Demo monitoring plan. Not clinically validated.", conditionLabel: "Diabetes daily wellness" },
];

let assignmentStatus = 200;
let holdAssignment = false;
let releaseAssignment: ((response: Response) => void) | null = null;

let sarahPlan = "Post-Operative Recovery Demo";

const sarahOverview = () => ({
  patientId: 7, fullName: "Sarah Miller", medicalCondition: "Post-operative recovery", timezone: "America/New_York",
  activeMonitoringPlanName: sarahPlan, reminderEnabled: true, reminderTime: "08:00",
  checkInDate: "2026-10-08", currentDate: "2026-10-08", todayStatus: "READY_FOR_REVIEW", todaySessionId: 101,
  sessionPlanName: "Post-Operative Recovery Demo", startedAt: "2026-10-08T12:00:00Z", completedAt: "2026-10-08T14:00:00Z",
  monitoringFlag: "RED", escalationReason: "Patient reported shortness of breath; clinician review required by configured safety rule.",
  questionsAnswered: 2, previousDaySession: false, requiresReview: true,
});

const sessionDetail = {
  sessionId: 101, patientId: 7, checkInDate: "2026-10-08", monitoringPlanName: "Post-Operative Recovery Demo",
  status: "READY_FOR_REVIEW", monitoringFlag: "RED",
  escalationReason: "Patient reported shortness of breath; clinician review required by configured safety rule.",
  startedAt: "2026-10-08T12:00:00Z", completedAt: "2026-10-08T14:00:00Z",
  sessionFacts: [
    { label: "Pain score", value: "5 / 10" },
    { label: "Shortness of breath", value: "No" },
    { label: "Temperature", value: "98.7" },
  ],
  turns: [
    {
      sequence: 1, question: "Tell me how you are feeling today in your own words.",
      patientResponse: "I've been dizzy since this morning.", inputMode: "VOICE", createdAt: "2026-10-08T12:04:00Z",
      clinicalNote: {
        noteText: "Patient reports dizziness beginning this morning.", noteLabel: "CareVoice Note",
        monitoringFlag: "YELLOW", escalationReason: null,
        facts: [{ label: "Dizziness", value: "Yes" }, { label: "Dizziness onset", value: "This morning" }],
      },
    },
    {
      sequence: 2, question: "Did you lose consciousness?", patientResponse: "No.", inputMode: "TEXT",
      createdAt: "2026-10-08T12:05:00Z", clinicalNote: null,
    },
  ],
};

function json(body: unknown, status = 200): Response {
  return jsonResponse(body, status);
}

beforeEach(() => {
  localStorage.clear();
  assignmentStatus = 200;
  holdAssignment = false;
  releaseAssignment = null;
  sarahPlan = "Post-Operative Recovery Demo";
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.endsWith("/api/auth/csrf")) return json({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/clinician/patients") && method === "GET") return json(patients);
    if (url.endsWith("/api/clinician/monitoring-plans")) return json(plans);
    if (/\/api\/clinician\/patients\/\d+\/monitoring-plan$/.test(url) && method === "PUT") {
      if (holdAssignment) {
        return new Promise<Response>((resolve) => { releaseAssignment = resolve; });
      }
      if (assignmentStatus !== 200) return json({ error: "unavailable" }, assignmentStatus);
      const body = JSON.parse(String(init?.body)) as { monitoringPlanId: number };
      const plan = plans.find((item) => item.id === body.monitoringPlanId);
      if (url.includes("/patients/7/") && plan) sarahPlan = plan.name;
      return json({ name: plan?.name ?? "", description: plan?.description ?? "" });
    }
    if (url.endsWith("/api/clinician/patients/7")) return json(sarahOverview());
    if (url.endsWith("/api/clinician/patients/8")) {
      return json({
        ...sarahOverview(), patientId: 8, fullName: "John Smith", medicalCondition: "Hypertension",
        timezone: "America/Chicago", activeMonitoringPlanName: "Hypertension Symptom Monitoring Demo",
        todayStatus: "NOT_STARTED", todaySessionId: null, sessionPlanName: null, startedAt: null, completedAt: null,
        monitoringFlag: null, escalationReason: null, questionsAnswered: 0, requiresReview: false, reminderEnabled: false, reminderTime: null,
      });
    }
    if (url.endsWith("/api/patients/7/history")) {
      return json({
        patientId: 7,
        sessions: [{
          sessionId: 101, startedAt: "2026-10-08T12:00:00Z", status: "READY_FOR_REVIEW", riskLevel: "RED",
          turnCount: 2, checkInDate: "2026-10-08", monitoringPlanName: "Post-Operative Recovery Demo",
          completedAt: "2026-10-08T14:00:00Z",
        }],
      });
    }
    if (url.endsWith("/api/patients/8/history")) return json({ patientId: 8, sessions: [] });
    if (url.includes("/api/patients/7/longitudinal-summary")) return json({ ...historySummary(), patientId: 7 });
    if (url.includes("/api/patients/8/longitudinal-summary")) return json({ ...emptySummary(), patientId: 8 });
    if (url.endsWith("/api/clinician/sessions/101")) return json(sessionDetail);
    if (/\/api\/clinician\/patients\/\d+\/conditions$/.test(url)) {
      const patientId = Number(url.match(/patients\/(\d+)/)?.[1]);
      if (patientId === 8) {
        return json([{ id: 80, conditionName: "Hypertension", monitoringCategory: "HYPERTENSION", active: true, primaryCondition: true }]);
      }
      return json([{ id: 70, conditionName: "Post-operative recovery", monitoringCategory: "POST_OPERATIVE", active: true, primaryCondition: true }]);
    }
    return json({ error: "missing" }, 500);
  }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("summarizes today's patients and filters them without storing the search", async () => {
  const user = userEvent.setup();
  const writes = vi.spyOn(Storage.prototype, "setItem");
  render(<ClinicianPatientsPage />);
  const overview = await screen.findByRole("region", { name: "Today's overview" });
  expect(within(overview).getByText("Patients").parentElement).toHaveTextContent("4");
  expect(within(overview).getByText("Not started").parentElement).toHaveTextContent("1");
  expect(within(overview).getByText("In progress").parentElement).toHaveTextContent("1");
  expect(within(overview).getByText("Completed").parentElement).toHaveTextContent("1");
  expect(within(overview).getByText("Ready for review").parentElement).toHaveTextContent("1");

  const sarah = screen.getByRole("article", { name: "Sarah Miller" });
  expect(within(sarah).getByText("Condition: Post-operative recovery")).toBeInTheDocument();
  expect(within(sarah).getByText("Monitoring Plan: Post-Operative Recovery Demo")).toBeInTheDocument();
  expect(within(sarah).getByText("Today: Ready for review")).toBeInTheDocument();
  expect(within(sarah).getByText("Requires review")).toBeInTheDocument();
  expect(within(sarah).getByText("Red monitoring flag")).toBeInTheDocument();
  const john = screen.getByRole("article", { name: "John Smith" });
  expect(within(john).getByText("Today: Not started")).toBeInTheDocument();
  expect(within(john).queryByText(/monitoring flag/i)).not.toBeInTheDocument();

  await user.type(screen.getByLabelText("Search patients"), "hyper");
  expect(screen.getByRole("article", { name: "John Smith" })).toBeInTheDocument();
  expect(screen.queryByRole("article", { name: "Sarah Miller" })).not.toBeInTheDocument();
  await user.clear(screen.getByLabelText("Search patients"));
  await user.click(screen.getByRole("button", { name: "Ready for review" }));
  expect(screen.getByRole("article", { name: "Sarah Miller" })).toBeInTheDocument();
  expect(screen.queryByRole("article", { name: "John Smith" })).not.toBeInTheDocument();
  expect(writes).not.toHaveBeenCalled();
  expect(screen.queryByText(/patient is critical|diagnosis confirmed/i)).not.toBeInTheDocument();
});

it("opens a patient with no check-in and a patient ready for review", async () => {
  const user = userEvent.setup();
  render(<ClinicianPatientsPage />);
  const john = await screen.findByRole("article", { name: "John Smith" });
  await user.click(within(john).getByRole("button", { name: "View Patient" }));
  expect(await screen.findByRole("heading", { name: "John Smith" })).toBeInTheDocument();
  expect(screen.getByText("Today's check-in: Not started")).toBeInTheDocument();
  expect(screen.getByText("Current Monitoring Plan: Hypertension Symptom Monitoring Demo")).toBeInTheDocument();
  expect(screen.getByText("Patient timezone: America/Chicago")).toBeInTheDocument();
  expect(screen.getByText("Daily reminder: Off")).toBeInTheDocument();
  expect(screen.getByText("Not enough monitoring history yet.")).toBeInTheDocument();
  expect(screen.getByText("No completed check-ins yet.")).toBeInTheDocument();
  expect(screen.queryByText(/green monitoring flag|yellow monitoring flag|red monitoring flag/i)).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Back to patients" }));
  expect(await screen.findByRole("heading", { name: "Patients" })).toBeInTheDocument();

  const sarah = await screen.findByRole("article", { name: "Sarah Miller" });
  await user.click(within(sarah).getByRole("button", { name: "View Patient" }));
  expect(await screen.findByRole("heading", { name: "Sarah Miller" })).toBeInTheDocument();
  expect(screen.getByText("Today's check-in: Ready for review")).toBeInTheDocument();
  expect(screen.getAllByText("Red monitoring flag").length).toBeGreaterThan(0);
  expect(screen.getByRole("region", { name: "Pain over time" })).toBeInTheDocument();
  expect(screen.getByText(/Plan snapshot:\s*Post-Operative Recovery Demo/)).toBeInTheDocument();
  expect(screen.getByText("CareVoice monitoring flags are decision-support signals and are not a diagnosis.")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Change Monitoring Plan" }));
  await user.click(await screen.findByRole("radio", { name: "Post-Operative Recovery Demo" }));
  expect(screen.getByRole("button", { name: "Confirm assignment" })).toBeDisabled();
  await user.click(screen.getByRole("radio", { name: "Diabetes Daily Wellness Demo" }));
  expect(screen.getByRole("button", { name: "Confirm assignment" })).toBeEnabled();
  await user.click(screen.getByRole("button", { name: "Confirm assignment" }));
  expect(await screen.findByText("Current Monitoring Plan: Diabetes Daily Wellness Demo")).toBeInTheDocument();
  expect(screen.getByText("Changes apply to future check-ins. An active check-in keeps its current plan.")).toBeInTheDocument();
});

it("reviews a session timeline and returns to the patient", async () => {
  const user = userEvent.setup();
  const writes = vi.spyOn(Storage.prototype, "setItem");
  render(<ClinicianPatientsPage />);
  const sarah = await screen.findByRole("article", { name: "Sarah Miller" });
  await user.click(within(sarah).getByRole("button", { name: "View Patient" }));
  await user.click(await screen.findByRole("button", { name: "View session" }));
  const timeline = await screen.findByRole("list", { name: "Questions and answers" });
  const items = within(timeline).getAllByRole("listitem");
  expect(items[0]).toHaveTextContent("Tell me how you are feeling today in your own words.");
  expect(items[0]).toHaveTextContent("Patient — Voice");
  expect(items[0]).toHaveTextContent("I've been dizzy since this morning.");
  expect(items[0]).toHaveTextContent("CareVoice Note");
  expect(items[0]).toHaveTextContent("Patient reports dizziness beginning this morning.");
  expect(items[0]).toHaveTextContent("Dizziness");
  expect(items[0]).toHaveTextContent("Yes");
  expect(items[1]).toHaveTextContent("Did you lose consciousness?");
  expect(items[1]).toHaveTextContent("Patient — Text");
  expect(items[1]).toHaveTextContent("CareVoice note not available.");
  expect(screen.getByText("Pain score").parentElement).toHaveTextContent("5 / 10");
  expect(screen.getByText("Shortness of breath").parentElement).toHaveTextContent("No");
  expect(screen.getByText("Temperature").parentElement).toHaveTextContent("98.7");
  expect(screen.queryByText("Medication taken")).not.toBeInTheDocument();
  expect(screen.getByText("Red monitoring flag")).toBeInTheDocument();
  expect(screen.getByText("Escalation reason: Patient reported shortness of breath; clinician review required by configured safety rule.")).toBeInTheDocument();
  expect(screen.queryByText(/gemini|provider/i)).not.toBeInTheDocument();
  expect(writes.mock.calls.every(([key]) => key !== "carevoice.checkin" || !String(writes.mock.calls).includes("dizzy"))).toBe(true);
  for (const [, value] of writes.mock.calls) {
    expect(String(value)).not.toContain("dizzy");
  }
  await user.click(screen.getByRole("button", { name: "Back to patient" }));
  expect(await screen.findByRole("heading", { name: "Sarah Miller" })).toBeInTheDocument();
});

it("assigns a different plan from the patient card and ignores the current plan", async () => {
  const user = userEvent.setup();
  holdAssignment = true;
  render(<ClinicianPatientsPage />);
  const john = await screen.findByRole("article", { name: "John Smith" });
  expect(within(john).getByText("Monitoring Plan: Hypertension Symptom Monitoring Demo")).toBeInTheDocument();
  await user.click(within(john).getByRole("button", { name: "Change Monitoring Plan" }));
  await user.click(await within(john).findByRole("radio", { name: "Hypertension Symptom Monitoring Demo" }));
  expect(within(john).getByRole("button", { name: "Confirm assignment" })).toBeDisabled();
  expect(vi.mocked(fetch).mock.calls.some(([url, init]) =>
    String(url).includes("/monitoring-plan") && init?.method === "PUT")).toBe(false);

  await user.click(within(john).getByRole("radio", { name: "General Daily Wellness" }));
  const confirm = within(john).getByRole("button", { name: "Confirm assignment" });
  expect(confirm).toBeEnabled();
  await user.click(confirm);
  expect(await within(john).findByText("Assigning...")).toBeInTheDocument();
  await waitFor(() => {
    expect(vi.mocked(fetch).mock.calls.some(([url, init]) =>
      String(url).endsWith("/api/clinician/patients/8/monitoring-plan") && init?.method === "PUT")).toBe(true);
  });
  const assignment = vi.mocked(fetch).mock.calls.find(([url, init]) =>
    String(url).endsWith("/api/clinician/patients/8/monitoring-plan") && init?.method === "PUT");
  expect(assignment?.[1]?.credentials).toBe("include");
  expect(assignment?.[1]?.body).toBe(JSON.stringify({ monitoringPlanId: 1 }));
  const headers = assignment?.[1]?.headers as Record<string, string>;
  expect(headers["X-CSRF-TOKEN"]).toBe("csrf-test");
  releaseAssignment?.(json({ name: "General Daily Wellness", description: "Demo monitoring plan. Not clinically validated." }));
  expect(await within(john).findByText("Monitoring Plan: General Daily Wellness")).toBeInTheDocument();
  expect(within(john).queryByRole("button", { name: "Confirm assignment" })).not.toBeInTheDocument();
});

it("shows an assignment error on the patient card without changing the current plan", async () => {
  const user = userEvent.setup();
  assignmentStatus = 502;
  render(<ClinicianPatientsPage />);
  const maria = await screen.findByRole("article", { name: "Maria Alvarez" });
  await user.click(within(maria).getByRole("button", { name: "Change Monitoring Plan" }));
  await user.click(await within(maria).findByRole("radio", { name: "Diabetes Daily Wellness Demo" }));
  await user.click(within(maria).getByRole("button", { name: "Confirm assignment" }));
  expect(await within(maria).findByRole("alert")).toHaveTextContent("Unable to assign monitoring plan. Please try again.");
  expect(within(maria).getByText("Monitoring Plan: General Daily Wellness")).toBeInTheDocument();
  expect(screen.queryByText(/stack|exception|unavailable/i)).not.toBeInTheDocument();
});
