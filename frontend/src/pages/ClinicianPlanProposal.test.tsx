import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ClinicianPatientsPage } from "./ClinicianPatientsPage.tsx";
import { emptySummary, jsonResponse } from "../test/historyFixtures.ts";

const overview = {
  patientId: 7, fullName: "Sarah Miller", medicalCondition: "Open-heart surgery recovery", timezone: "America/New_York",
  activeMonitoringPlanName: "General Daily Wellness", reminderEnabled: false, reminderTime: null,
  checkInDate: "2026-10-08", currentDate: "2026-10-08", todayStatus: "NOT_STARTED", todaySessionId: null,
  sessionPlanName: null, startedAt: null, completedAt: null, monitoringFlag: null, escalationReason: null,
  questionsAnswered: 0, previousDaySession: false, requiresReview: false,
};

const pain = {
  id: 11, fieldCode: "PAIN_SCORE", displayName: "Pain", questionText: "How would you rate your pain today from 0 to 10?",
  displayOrder: 1, required: true, enabled: true, rationale: "Included for recovery monitoring.",
};
const medication = {
  id: 12, fieldCode: "MEDICATION_TAKEN", displayName: "Medication",
  questionText: "Have you taken your prescribed medications today?",
  displayOrder: 2, required: true, enabled: true, rationale: null,
};

let proposal: Record<string, unknown> | null = null;
let planName = "General Daily Wellness";

beforeEach(() => {
  localStorage.clear();
  planName = "General Daily Wellness";
  proposal = null;
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.endsWith("/api/auth/csrf")) return jsonResponse({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/clinician/patients") && method === "GET") {
      return jsonResponse([{
        patientId: 7, fullName: "Sarah Miller", medicalCondition: "Open-heart surgery recovery", timezone: "America/New_York",
        monitoringPlanName: planName, todayCheckInDate: "2026-10-08", todayStatus: "NOT_STARTED",
        todaySessionId: null, requiresReview: false,
      }]);
    }
    if (url.endsWith("/api/clinician/monitoring-plans")) return jsonResponse([]);
    if (url.endsWith("/api/clinician/patients/7") && method === "GET") {
      return jsonResponse({ ...overview, activeMonitoringPlanName: planName });
    }
    if (url.endsWith("/api/patients/7/history")) return jsonResponse({ patientId: 7, sessions: [] });
    if (url.includes("/api/patients/7/longitudinal-summary")) return jsonResponse({ ...emptySummary(), patientId: 7 });
    if (url.endsWith("/api/clinician/patients/7/conditions")) {
      return jsonResponse([
        { id: 1, conditionName: "Open-heart surgery recovery", monitoringCategory: "POST_OPERATIVE", active: true, primaryCondition: true },
        { id: 2, conditionName: "Hypertension", monitoringCategory: "HYPERTENSION", active: true, primaryCondition: false },
      ]);
    }
    if (url.endsWith("/api/clinician/patients/7/monitoring-plan-proposals") && method === "GET") {
      return jsonResponse(proposal ? [{ id: 4, patientId: 7, status: "DRAFT", createdAt: "2026-10-08T16:00:00Z" }] : []);
    }
    if (url.endsWith("/api/clinician/patients/7/monitoring-plan-proposals") && method === "POST") {
      proposal = draftProposal();
      return jsonResponse(proposal, 201);
    }
    if (url.endsWith("/api/clinician/monitoring-plan-proposals/4") && method === "GET") return jsonResponse(proposal);
    if (url.endsWith("/api/clinician/monitoring-plan-proposals/4/questions/11") && method === "PUT") {
      const body = JSON.parse(String(init?.body)) as { questionText: string; required: boolean; enabled: boolean };
      pain.questionText = body.questionText;
      pain.required = body.required;
      pain.enabled = body.enabled;
      proposal = draftProposal();
      return jsonResponse(proposal);
    }
    if (url.endsWith("/api/clinician/monitoring-plan-proposals/4/approve") && method === "POST") {
      planName = "Personalized Daily Monitoring Plan";
      proposal = null;
      return jsonResponse({ ...draftProposal(), status: "APPROVED", approvedMonitoringPlanName: planName });
    }
    if (url.includes("/api/clinician/monitoring-fields")) {
      return jsonResponse([{ code: "SLEEP_QUALITY", displayName: "Sleep Quality", planAskable: true }]);
    }
    return jsonResponse({ error: "missing" }, 500);
  }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("lets a clinician review, edit, and approve a suggested plan", async () => {
  const user = userEvent.setup();
  render(<ClinicianPatientsPage />);
  await user.click(await screen.findByRole("button", { name: "View Patient" }));
  const section = await screen.findByRole("region", { name: "Personalized Plan" });
  await user.click(await within(section).findByRole("button", { name: "Generate Suggested Plan" }));
  expect(await within(section).findByRole("heading", { name: "AI-Suggested Monitoring Plan" })).toBeInTheDocument();
  expect(within(section).getByText("Open-heart surgery recovery (primary)")).toBeInTheDocument();
  expect(within(section).getByText("Hypertension")).toBeInTheDocument();
  expect(within(section).getByText("How would you rate your pain today from 0 to 10?")).toBeInTheDocument();
  await user.click(within(section).getByRole("button", { name: "Edit Pain" }));
  const wording = within(section).getByLabelText("Question wording");
  await user.clear(wording);
  await user.type(wording, "On a scale from 0 to 10, what is your pain level today?");
  await user.click(within(section).getByRole("button", { name: "Save wording" }));
  expect(await within(section).findByText("On a scale from 0 to 10, what is your pain level today?")).toBeInTheDocument();
  await user.click(within(section).getByRole("button", { name: "Approve & Assign" }));
  expect(await screen.findByText("Current Monitoring Plan: Personalized Daily Monitoring Plan")).toBeInTheDocument();
  expect(within(section).getByRole("button", { name: "Generate Suggested Plan" })).toBeInTheDocument();
});

function draftProposal() {
  return {
    id: 4,
    patientId: 7,
    status: "DRAFT",
    createdAt: "2026-10-08T16:00:00Z",
    approvedAt: null,
    rejectedAt: null,
    approvedMonitoringPlanId: null,
    approvedMonitoringPlanName: null,
    conditions: [
      { sourcePatientConditionId: 1, conditionName: "Open-heart surgery recovery", monitoringCategory: "POST_OPERATIVE", primaryCondition: true },
      { sourcePatientConditionId: 2, conditionName: "Hypertension", monitoringCategory: "HYPERTENSION", primaryCondition: false },
    ],
    conditionFamilies: ["POST_OPERATIVE", "HYPERTENSION"],
    warnings: [],
    questions: [pain, medication],
  };
}
