import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import App from "./App.tsx";
import { historySummary, jsonResponse, sessionHistory } from "./test/historyFixtures.ts";

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes("/api/auth/me")) return jsonResponse({
      authenticated: true, accountId: 1, username: "ada01", role: "PATIENT",
      patient: { id: 7, fullName: "Ada Lovelace", medicalCondition: "Recovery", timezone: "America/New_York" },
    });
    if (url.includes("/api/auth/csrf")) return jsonResponse({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/me/monitoring-plan")) {
      return jsonResponse({ name: "General Daily Wellness", description: "Demo monitoring plan. Not clinically validated." });
    }
    if (url.includes("longitudinal-summary")) return jsonResponse(historySummary());
    if (url.endsWith("/history")) return jsonResponse(sessionHistory(3));
    if (url.endsWith("/api/patients")) return jsonResponse({ id: 7 }, 201);
    if (url.endsWith("/api/me/check-in/today")) return todayResponse(init);
    if (url.endsWith("/messages")) return jsonResponse({ sessionId: 3, status: "COMPLETED", riskLevel: "GREEN", conversationComplete: true, nextQuestion: null });
    throw new Error("Unexpected request");
  }));
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("preserves an unsent text draft when navigating to history and back", async () => {
  const user = userEvent.setup();
  render(<App />);
  await user.click(await screen.findByRole("button", { name: "Start Check-In" }));
  await screen.findByRole("heading", { name: "How are you feeling?" });
  // Voice is unsupported in jsdom, so the text form opens automatically.
  await user.type(await screen.findByLabelText("Type your answer"), "An unsent private draft");
  await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: "History" }));
  await screen.findByRole("region", { name: "Recent check-ins" });
  await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: "Today's Check-In" }));
  expect(screen.getByLabelText("Type your answer")).toHaveValue("An unsent private draft");
  expect(localStorage.getItem("carevoice.checkin")).not.toContain("private draft");
  expect(vi.mocked(fetch).mock.calls.filter(([url, init]) =>
    String(url).endsWith("/api/me/check-in/today") && (init?.method ?? "GET").toUpperCase() === "POST")).toHaveLength(1);
});

it("connects completion to today's responses and health history while storing identifiers only", async () => {
  const user = userEvent.setup();
  const writes = vi.spyOn(Storage.prototype, "setItem");
  render(<App />);
  await user.click(await screen.findByRole("button", { name: "Start Check-In" }));
  await user.type(await screen.findByLabelText("Type your answer"), "My private answer");
  await user.click(screen.getByRole("button", { name: "Send answer" }));
  await screen.findByText("Your responses have been saved.");
  expect(screen.getByText("1 response saved · Check-in complete")).toBeInTheDocument();
  expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "View today's responses" }));
  expect(await screen.findByText("I kept waking up.")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Back to health history" }));
  await screen.findByRole("region", { name: "Recent check-ins" });
  await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: "Today's Check-In" }));
  await user.click(screen.getByRole("button", { name: "View health history" }));
  await screen.findByRole("region", { name: "Recent check-ins" });
  expect(writes).toHaveBeenCalled();
  for (const [key, value] of writes.mock.calls) {
    expect(key).toBe("carevoice.checkin");
    expect(JSON.parse(value)).toEqual({ sessionId: 3 });
  }
});

it("restores a check-in from the session id and the signed-in patient", async () => {
  localStorage.setItem("carevoice.checkin", JSON.stringify({
    patientId: 99, sessionId: 3, email: "secret@example.com",
  }));
  const user = userEvent.setup();
  render(<App />);
  await screen.findByText("Your responses have been saved.");
  await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: "History" }));
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/patients/7/longitudinal-summary?limit=30"), expect.anything()));
  expect(vi.mocked(fetch).mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  const stored = localStorage.getItem("carevoice.checkin") ?? "";
  expect(JSON.parse(stored)).toEqual({ sessionId: 3 });
  expect(stored).not.toContain("secret@example.com");
});

function todayResponse(init?: RequestInit): Response {
  if ((init?.method ?? "GET").toUpperCase() === "POST") {
    return jsonResponse({ sessionId: 3, status: "IN_PROGRESS", nextQuestion: "How are you feeling?" });
  }
  const storedId = Number(JSON.parse(localStorage.getItem("carevoice.checkin") ?? "{}").sessionId);
  if (storedId === 3) {
    return jsonResponse({
      checkInDate: "2026-10-08", currentDate: "2026-10-08", timezone: "America/New_York",
      status: "IN_PROGRESS", sessionId: 3, monitoringPlanName: "General Daily Wellness",
      startedAt: "2026-10-08T15:00:00Z", completedAt: null, previousDaySession: false,
    });
  }
  return jsonResponse({
    checkInDate: "2026-10-08", currentDate: "2026-10-08", timezone: "America/New_York",
    status: "NOT_STARTED", sessionId: null, monitoringPlanName: "General Daily Wellness",
    startedAt: null, completedAt: null, previousDaySession: false,
  });
}
