import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { PatientCheckInPage } from "./PatientCheckInPage.tsx";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith("/api/auth/csrf")) return json({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/me/check-in/today")) {
      return json({
        checkInDate: "2026-10-08", currentDate: "2026-10-08", timezone: "America/New_York",
        status: "NOT_STARTED", sessionId: null, monitoringPlanName: "General Daily Wellness",
        startedAt: null, completedAt: null, previousDaySession: false,
      });
    }
    if (url.endsWith("/api/me/reminder-preference")) {
      return json({ enabled: true, reminderTime: "08:00", timezone: "America/New_York" });
    }
    if (url.endsWith("/api/me/notifications")) return json([]);
    if (url.endsWith("/api/me/conditions")) {
      return json([
        { conditionName: "Open-heart surgery recovery", monitoringCategory: "POST_OPERATIVE", primary: true },
        { conditionName: "Hypertension", monitoringCategory: "HYPERTENSION", primary: false },
        { conditionName: "Type 2 diabetes", monitoringCategory: "DIABETES", primary: false },
      ]);
    }
    return json({ error: "missing" }, 500);
  }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("shows active conditions as read-only and keeps the monitoring plan separate", async () => {
  render(<PatientCheckInPage patientId={7} />);
  expect(await screen.findByRole("heading", { name: "Health Conditions" })).toBeInTheDocument();
  expect(screen.getByText("Open-heart surgery recovery")).toBeInTheDocument();
  expect(screen.getByText("Hypertension")).toBeInTheDocument();
  expect(screen.getByText("Type 2 diabetes")).toBeInTheDocument();
  expect(screen.getByText("Monitoring Plan: General Daily Wellness")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "+ Add Condition" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Set as Primary" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Generate Suggested Plan" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Approve & Assign" })).not.toBeInTheDocument();
});
