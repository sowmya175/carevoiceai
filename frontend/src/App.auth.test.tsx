import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import App from "./App.tsx";
import { historySummary, jsonResponse, sessionHistory } from "./test/historyFixtures.ts";

const patient = {
  authenticated: true,
  accountId: 4,
  username: "ada01",
  role: "PATIENT",
  patient: {
    id: 7,
    fullName: "Ada Lovelace",
    medicalCondition: "Recovery",
    timezone: "America/New_York",
  },
};

const clinician = {
  authenticated: true,
  accountId: 2,
  username: "doctor1",
  role: "CLINICIAN",
  patient: null,
};

const clinicianPatients = [
  { patientId: 7, fullName: "Sarah Miller", medicalCondition: "Post-operative recovery", timezone: "America/New_York", monitoringPlanName: "Post-Operative Recovery Demo", todayCheckInDate: "2026-10-08", todayStatus: "COMPLETED", todaySessionId: 101 },
  { patientId: 8, fullName: "John Smith", medicalCondition: "Hypertension", timezone: "America/Chicago", monitoringPlanName: "Hypertension Symptom Monitoring Demo", todayCheckInDate: "2026-10-08", todayStatus: "NOT_STARTED", todaySessionId: null },
  { patientId: 9, fullName: "Maria Alvarez", medicalCondition: "General recovery", timezone: "America/Denver", monitoringPlanName: "General Daily Wellness", todayCheckInDate: "2026-10-08", todayStatus: "IN_PROGRESS", todaySessionId: 88 },
];

const demoPlans = [
  { id: 1, name: "General Daily Wellness", description: "Demo monitoring plan. Not clinically validated.", conditionLabel: "Daily wellness" },
  { id: 2, name: "Post-Operative Recovery Demo", description: "Demo monitoring plan. Not clinically validated.", conditionLabel: "Post-operative recovery" },
  { id: 3, name: "Hypertension Symptom Monitoring Demo", description: "Demo monitoring plan. Not clinically validated. This does not replace blood-pressure measurement.", conditionLabel: "Hypertension symptoms" },
  { id: 4, name: "Diabetes Daily Wellness Demo", description: "Demo monitoring plan. Not clinically validated. This does not replace glucose monitoring.", conditionLabel: "Diabetes daily wellness" },
];

let session: "none" | "patient" | "clinician" = "none";

beforeEach(() => {
  localStorage.clear();
  session = "none";
  clinicianPatients[0].monitoringPlanName = "Post-Operative Recovery Demo";
  clinicianPatients[1].monitoringPlanName = "Hypertension Symptom Monitoring Demo";
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    expect(init?.credentials).toBe("include");
    if (url.endsWith("/api/auth/csrf")) return jsonResponse({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/auth/register/patient")) return jsonResponse({ ...patient, authenticated: false }, 201);
    if (url.endsWith("/api/auth/login/patient")) {
      session = "patient";
      return jsonResponse(patient);
    }
    if (url.endsWith("/api/auth/login/clinician")) {
      session = "clinician";
      return jsonResponse(clinician);
    }
    if (url.endsWith("/api/auth/logout")) {
      session = "none";
      return new Response(null, { status: 204 });
    }
    if (url.endsWith("/api/auth/me")) {
      if (session === "patient") return jsonResponse(patient);
      if (session === "clinician") return jsonResponse(clinician);
      return jsonResponse({ error: "Authentication required." }, 401);
    }
    if (url.endsWith("/api/me/monitoring-plan")) {
      return session === "patient"
        ? jsonResponse({ name: "General Daily Wellness", description: "Demo monitoring plan. Not clinically validated." })
        : jsonResponse({ error: "Access denied." }, 403);
    }
    if (url.endsWith("/api/clinician/patients")) {
      return session === "clinician" ? jsonResponse(clinicianPatients) : jsonResponse({ error: "Access denied." }, 403);
    }
    if (url.endsWith("/api/clinician/monitoring-plans")) {
      return session === "clinician" ? jsonResponse(demoPlans) : jsonResponse({ error: "Access denied." }, 403);
    }
    if (/\/api\/clinician\/patients\/\d+\/monitoring-plan$/.test(url) && init?.method === "PUT") {
      if (session !== "clinician") return jsonResponse({ error: "Access denied." }, 403);
      const body = JSON.parse(String(init.body)) as { monitoringPlanId: number };
      const plan = demoPlans.find((item) => item.id === body.monitoringPlanId);
      const patientId = Number(url.match(/patients\/(\d+)/)?.[1]);
      const listed = clinicianPatients.find((item) => item.patientId === patientId);
      if (listed && plan) listed.monitoringPlanName = plan.name;
      return jsonResponse({
        monitoringPlanId: plan?.id,
        name: plan?.name,
        description: plan?.description,
        conditionLabel: plan?.conditionLabel,
      });
    }
    if (url.includes("longitudinal-summary")) return jsonResponse(historySummary());
    if (url.endsWith("/history")) return jsonResponse(sessionHistory(3));
    if (url.endsWith("/api/me/check-in/today")) {
      if ((init?.method ?? "GET").toUpperCase() === "POST") {
        return jsonResponse({ sessionId: 3, status: "IN_PROGRESS", nextQuestion: "How are you feeling?" });
      }
      return jsonResponse({
        checkInDate: "2026-10-08", currentDate: "2026-10-08", timezone: "America/New_York",
        status: "NOT_STARTED", sessionId: null, monitoringPlanName: "General Daily Wellness",
        startedAt: null, completedAt: null, previousDaySession: false,
      });
    }
    if (url.endsWith("/messages")) return jsonResponse({ sessionId: 3, status: "COMPLETED", riskLevel: "GREEN", conversationComplete: true, nextQuestion: null });
    throw new Error(`Unexpected request ${url}`);
  }));
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function storedText(): string {
  return JSON.stringify(localStorage);
}

it("offers patient login, doctor login, and patient registration", async () => {
  render(<App />);
  expect(await screen.findByRole("button", { name: "Patient Login" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Doctor Login" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Create Patient Account" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /create doctor/i })).not.toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("Email")).not.toBeInTheDocument();
  expect(screen.queryByText(/Welcome,/)).not.toBeInTheDocument();
});

it("registers a patient with a username and no email or doctor option", async () => {
  const user = userEvent.setup({ delay: null });
  render(<App />);
  await user.click(await screen.findByRole("button", { name: "Create Patient Account" }));
  const form = screen.getByRole("form", { name: "Create patient account" });
  expect(within(form).queryByLabelText("Email")).not.toBeInTheDocument();
  expect(within(form).queryByRole("checkbox")).not.toBeInTheDocument();
  expect(within(form).queryByText(/doctor/i)).not.toBeInTheDocument();
  await user.type(within(form).getByLabelText("Full Name"), "Ada Lovelace");
  await user.type(within(form).getByLabelText("Username"), "ada01");
  await user.type(within(form).getByLabelText("Password"), "correct-horse-battery");
  await user.type(within(form).getByLabelText("Medical Condition"), "Recovery");
  await user.selectOptions(within(form).getByLabelText("Timezone"), "America/New_York");
  await user.click(within(form).getByRole("button", { name: "Create Patient Account" }));
  expect(await screen.findByText("Welcome, Ada Lovelace")).toBeInTheDocument();
  expect(screen.getByText("Condition: Recovery")).toBeInTheDocument();
  expect(await screen.findAllByText("Monitoring Plan: General Daily Wellness")).toHaveLength(2);
  expect(screen.queryByRole("button", { name: "Change Monitoring Plan" })).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Patients" })).not.toBeInTheDocument();
  const registerCall = vi.mocked(fetch).mock.calls.find(([url]) => String(url).endsWith("/api/auth/register/patient"));
  const body = JSON.parse(String(registerCall?.[1]?.body));
  expect(body.username).toBe("ada01");
  expect(body.email).toBeUndefined();
  expect(body.role).toBeUndefined();
  expect(storedText()).not.toContain("ada01");
  expect(storedText()).not.toContain("correct-horse-battery");
  expect(storedText()).not.toContain("Recovery");
  expect(storedText()).not.toContain("token");
  expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes("/api/clinician/patients"))).toBe(false);
}, 20000);

it("signs a patient into the patient application", async () => {
  const user = userEvent.setup({ delay: null });
  render(<App />);
  await user.click(await screen.findByRole("button", { name: "Patient Login" }));
  const form = screen.getByRole("form", { name: "Patient login" });
  await user.type(within(form).getByLabelText("Username"), "ada01");
  await user.type(within(form).getByLabelText("Password"), "correct-horse-battery");
  await user.click(within(form).getByRole("button", { name: "Patient Login" }));
  expect(await screen.findByText("Welcome, Ada Lovelace")).toBeInTheDocument();
  expect(await screen.findAllByText("Monitoring Plan: General Daily Wellness")).toHaveLength(2);
  expect(screen.queryByRole("button", { name: "Change Monitoring Plan" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Today's Check-In" })).toBeInTheDocument();
  expect(screen.queryByText("CareVoice Clinical")).not.toBeInTheDocument();
  expect(storedText()).not.toContain("ada01");
  expect(storedText()).not.toContain("correct-horse-battery");
});

it("signs a doctor into the clinician list and keeps a patient out of it", async () => {
  const user = userEvent.setup({ delay: null });
  render(<App />);
  await user.click(await screen.findByRole("button", { name: "Doctor Login" }));
  const form = screen.getByRole("form", { name: "Doctor login" });
  await user.type(within(form).getByLabelText("Username"), "doctor1");
  await user.type(within(form).getByLabelText("Password"), "correct-horse-battery");
  await user.click(within(form).getByRole("button", { name: "Doctor Login" }));
  expect(await screen.findByText("CareVoice Clinical")).toBeInTheDocument();
  expect(screen.getByText("Welcome, Doctor")).toBeInTheDocument();
  expect(await screen.findByRole("heading", { name: "Sarah Miller" })).toBeInTheDocument();
  expect(screen.getByText("Condition: Post-operative recovery")).toBeInTheDocument();
  expect(screen.getByText("Monitoring Plan: Post-Operative Recovery Demo")).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "John Smith" })).toBeInTheDocument();
  expect(screen.getByText("Condition: Hypertension")).toBeInTheDocument();
  expect(screen.getByText("Monitoring Plan: Hypertension Symptom Monitoring Demo")).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Maria Alvarez" })).toBeInTheDocument();
  expect(screen.getByText("Today: Completed")).toBeInTheDocument();
  expect(screen.getByText("Today: Not started")).toBeInTheDocument();
  expect(screen.getByText("Today: In progress")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Today's Check-In" })).not.toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/clinician/patients"), expect.objectContaining({ credentials: "include" }));
  expect(storedText()).not.toContain("doctor1");
});

it("lets a doctor assign a different demo plan", async () => {
  session = "clinician";
  const user = userEvent.setup();
  render(<App />);
  const sarah = await screen.findByRole("article", { name: "Sarah Miller" });
  expect(within(sarah).getByText("Monitoring Plan: Post-Operative Recovery Demo")).toBeInTheDocument();
  await user.click(within(sarah).getByRole("button", { name: "Change Monitoring Plan" }));
  expect(await within(sarah).findByRole("radio", { name: "General Daily Wellness" })).toBeInTheDocument();
  expect(within(sarah).getByRole("radio", { name: "Post-Operative Recovery Demo" })).toBeInTheDocument();
  expect(within(sarah).getByRole("radio", { name: "Hypertension Symptom Monitoring Demo" })).toBeInTheDocument();
  expect(within(sarah).getByRole("radio", { name: "Diabetes Daily Wellness Demo" })).toBeInTheDocument();
  await user.click(within(sarah).getByRole("radio", { name: "Diabetes Daily Wellness Demo" }));
  await user.click(within(sarah).getByRole("button", { name: "Confirm assignment" }));
  expect(await within(sarah).findByText("Monitoring Plan: Diabetes Daily Wellness Demo")).toBeInTheDocument();
  expect(screen.getByText("Monitoring Plan: Hypertension Symptom Monitoring Demo")).toBeInTheDocument();
  const assignment = vi.mocked(fetch).mock.calls.find(([url, init]) =>
    String(url).endsWith("/api/clinician/patients/7/monitoring-plan") && init?.method === "PUT");
  expect(assignment?.[1]?.body).toBe(JSON.stringify({ monitoringPlanId: 4 }));
  expect(storedText()).not.toContain("Diabetes Daily Wellness Demo");
});

it("restores the signed-in role from the session after refresh", async () => {
  session = "clinician";
  localStorage.setItem("carevoice.checkin", JSON.stringify({ sessionId: 3, username: "stored-user", role: "PATIENT" }));
  const writes = vi.spyOn(Storage.prototype, "setItem");
  render(<App />);
  expect(await screen.findByText("Welcome, Doctor")).toBeInTheDocument();
  expect(screen.queryByText("stored-user")).not.toBeInTheDocument();
  const me = vi.mocked(fetch).mock.calls.find(([url]) => String(url).endsWith("/api/auth/me"));
  expect(me?.[1]?.credentials).toBe("include");
  expect(writes).not.toHaveBeenCalled();
});

it("opens today's check-in and history for a patient, then returns to the login selector", async () => {
  session = "patient";
  const user = userEvent.setup();
  render(<App />);
  await user.click(await screen.findByRole("button", { name: "Start Check-In" }));
  expect(await screen.findByRole("heading", { name: "How are you feeling?" })).toBeInTheDocument();
  expect(JSON.parse(localStorage.getItem("carevoice.checkin") ?? "{}")).toEqual({ sessionId: 3 });
  await user.click(within(screen.getByRole("navigation", { name: "Patient navigation" })).getByRole("button", { name: "History" }));
  await screen.findByRole("region", { name: "Recent check-ins" });
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    expect.stringContaining("/api/patients/7/longitudinal-summary?limit=30"),
    expect.objectContaining({ credentials: "include" }),
  ));
  await user.click(screen.getByRole("button", { name: "Sign Out" }));
  expect(await screen.findByRole("button", { name: "Patient Login" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Doctor Login" })).toBeInTheDocument();
  expect(localStorage.getItem("carevoice.checkin")).toBeNull();
  expect(storedText()).not.toContain("ada01");
  expect(storedText()).not.toContain("Recovery");
});
