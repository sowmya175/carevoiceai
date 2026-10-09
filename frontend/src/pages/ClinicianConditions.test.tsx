import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ClinicianPatientsPage } from "./ClinicianPatientsPage.tsx";
import { jsonResponse } from "../test/historyFixtures.ts";
import { emptySummary } from "../test/historyFixtures.ts";

const overview = {
  patientId: 7, fullName: "Sarah Miller", medicalCondition: "Open-heart surgery recovery", timezone: "America/New_York",
  activeMonitoringPlanName: "General Daily Wellness", reminderEnabled: false, reminderTime: null,
  checkInDate: "2026-10-08", currentDate: "2026-10-08", todayStatus: "NOT_STARTED", todaySessionId: null,
  sessionPlanName: null, startedAt: null, completedAt: null, monitoringFlag: null, escalationReason: null,
  questionsAnswered: 0, previousDaySession: false, requiresReview: false,
};

let conditions = [
  { id: 1, conditionName: "Open-heart surgery recovery", monitoringCategory: "POST_OPERATIVE", active: true, primaryCondition: true },
  { id: 2, conditionName: "Hypertension", monitoringCategory: "HYPERTENSION", active: true, primaryCondition: false },
  { id: 3, conditionName: "Type 2 diabetes", monitoringCategory: "DIABETES", active: true, primaryCondition: false },
];
let legacyCondition = "Open-heart surgery recovery";

beforeEach(() => {
  localStorage.clear();
  legacyCondition = "Open-heart surgery recovery";
  conditions = [
    { id: 1, conditionName: "Open-heart surgery recovery", monitoringCategory: "POST_OPERATIVE", active: true, primaryCondition: true },
    { id: 2, conditionName: "Hypertension", monitoringCategory: "HYPERTENSION", active: true, primaryCondition: false },
    { id: 3, conditionName: "Type 2 diabetes", monitoringCategory: "DIABETES", active: true, primaryCondition: false },
  ];
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.endsWith("/api/auth/csrf")) return jsonResponse({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/clinician/patients") && method === "GET") {
      return jsonResponse([{
        patientId: 7, fullName: "Sarah Miller", medicalCondition: legacyCondition, timezone: "America/New_York",
        monitoringPlanName: "General Daily Wellness", todayCheckInDate: "2026-10-08", todayStatus: "NOT_STARTED",
        todaySessionId: null, requiresReview: false,
      }]);
    }
    if (url.endsWith("/api/clinician/monitoring-plans")) return jsonResponse([]);
    if (url.endsWith("/api/clinician/patients/7") && method === "GET") {
      return jsonResponse({ ...overview, medicalCondition: legacyCondition });
    }
    if (url.endsWith("/api/patients/7/history")) return jsonResponse({ patientId: 7, sessions: [] });
    if (url.includes("/api/patients/7/longitudinal-summary")) return jsonResponse({ ...emptySummary(), patientId: 7 });
    if (url.endsWith("/api/clinician/patients/7/conditions") && method === "GET") return jsonResponse(conditions);
    if (url.endsWith("/api/clinician/patients/7/conditions") && method === "POST") {
      const body = JSON.parse(String(init?.body)) as { conditionName: string; monitoringCategory: string; primaryCondition: boolean };
      const created = { id: 4, conditionName: body.conditionName, monitoringCategory: body.monitoringCategory, active: true, primaryCondition: body.primaryCondition };
      conditions = [...conditions, created];
      return jsonResponse(created, 201);
    }
    if (/\/conditions\/\d+$/.test(url) && method === "PUT") {
      const id = Number(url.match(/conditions\/(\d+)/)?.[1]);
      const body = JSON.parse(String(init?.body)) as { conditionName: string; monitoringCategory: string; active: boolean; primaryCondition: boolean };
      conditions = conditions.map((condition) => {
        if (body.primaryCondition && condition.id !== id) return { ...condition, primaryCondition: false };
        if (condition.id !== id) return condition;
        return { ...condition, ...body };
      });
      if (body.primaryCondition) legacyCondition = body.conditionName;
      const saved = conditions.find((condition) => condition.id === id);
      return jsonResponse(saved);
    }
    return jsonResponse({ error: "missing" }, 500);
  }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("lets a clinician review and update conditions without treating them as the monitoring plan", async () => {
  const user = userEvent.setup();
  render(<ClinicianPatientsPage />);
  await user.click(await screen.findByRole("button", { name: "View Patient" }));
  expect(await screen.findByRole("heading", { name: "Primary" })).toBeInTheDocument();
  const primary = screen.getByRole("article", { name: "Open-heart surgery recovery" });
  expect(within(primary).getByText("Post-operative")).toBeInTheDocument();
  expect(within(primary).queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
  expect(screen.getByRole("article", { name: "Hypertension" })).toBeInTheDocument();
  expect(screen.getByRole("article", { name: "Type 2 diabetes" })).toBeInTheDocument();
  expect(screen.getByText("Current Monitoring Plan: General Daily Wellness")).toBeInTheDocument();
  expect(screen.getByText("Condition: Open-heart surgery recovery")).toBeInTheDocument();
  expect(screen.getByText("Conditions describe the patient's current health context. The monitoring plan controls the questions CareVoice currently asks.")).toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: "+ Add Condition" }));
  await user.type(screen.getByLabelText("Condition / Procedure"), "Asthma");
  await user.selectOptions(screen.getByLabelText("Category"), "Respiratory");
  await user.click(screen.getByRole("button", { name: "Save" }));
  expect(await screen.findByRole("article", { name: "Asthma" })).toBeInTheDocument();
  expect(within(screen.getByRole("article", { name: "Asthma" })).getByText("Respiratory")).toBeInTheDocument();

  await user.click(within(screen.getByRole("article", { name: "Type 2 diabetes" })).getByRole("button", { name: "Edit" }));
  const name = screen.getByLabelText("Condition / Procedure");
  await user.clear(name);
  await user.type(name, "Type 2 diabetes mellitus");
  await user.click(screen.getByRole("button", { name: "Save" }));
  expect(await screen.findByRole("article", { name: "Type 2 diabetes mellitus" })).toBeInTheDocument();

  await user.click(within(screen.getByRole("article", { name: "Hypertension" })).getByRole("button", { name: "Set as Primary" }));
  expect(await screen.findByText("Condition: Hypertension")).toBeInTheDocument();
  expect(within(screen.getByRole("article", { name: "Hypertension" })).queryByRole("button", { name: "Set as Primary" })).not.toBeInTheDocument();

  await user.click(within(screen.getByRole("article", { name: "Type 2 diabetes mellitus" })).getByRole("button", { name: "Deactivate" }));
  await waitFor(() => {
    expect(screen.queryByRole("article", { name: "Type 2 diabetes mellitus" })).not.toBeInTheDocument();
  });
  expect(screen.getByText("Current Monitoring Plan: General Daily Wellness")).toBeInTheDocument();
});
