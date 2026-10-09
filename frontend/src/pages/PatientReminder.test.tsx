import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PatientCheckInPage } from "./PatientCheckInPage.tsx";

const OPENING = "Tell me how you are feeling today in your own words.";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function today(status: string, previousDaySession = false) {
  return {
    checkInDate: previousDaySession ? "2026-10-07" : "2026-10-08",
    currentDate: "2026-10-08",
    timezone: "America/New_York",
    status,
    sessionId: status === "NOT_STARTED" ? null : 3,
    monitoringPlanName: "General Daily Wellness",
    startedAt: null,
    completedAt: null,
    previousDaySession,
  };
}

function notification(messageKey: string, message: string) {
  return {
    id: 101,
    type: "DAILY_CHECK_IN",
    messageKey,
    message,
    createdAt: "2026-10-08T12:00:00Z",
    read: false,
  };
}

let todayBody = today("NOT_STARTED");
let notifications = [notification("DAILY_CHECK_IN_START", "Good morning. Your CareVoice daily check-in is ready.")];

beforeEach(() => {
  localStorage.clear();
  todayBody = today("NOT_STARTED");
  notifications = [notification("DAILY_CHECK_IN_START", "Good morning. Your CareVoice daily check-in is ready.")];
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.endsWith("/api/auth/csrf")) return json({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/me/check-in/today") && method === "POST") {
      return json({ sessionId: 3, status: "IN_PROGRESS", nextQuestion: OPENING });
    }
    if (url.endsWith("/api/me/check-in/today")) return json(todayBody);
    if (url.endsWith("/api/me/reminder-preference") && method === "PUT") {
      const body = JSON.parse(String(init?.body)) as { enabled: boolean; reminderTime: string };
      return json({ ...body, timezone: "America/New_York" });
    }
    if (url.endsWith("/api/me/reminder-preference")) {
      return json({ enabled: true, reminderTime: "08:00", timezone: "America/New_York" });
    }
    if (url.endsWith("/api/me/notifications/101/read")) return new Response(null, { status: 204 });
    if (url.endsWith("/api/me/notifications")) return json(notifications);
    return json({ error: "missing" }, 500);
  }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("daily reminder settings and banner", () => {
  it("shows the reminder time, timezone, and saves the preference", async () => {
    const user = userEvent.setup();
    notifications = [];
    render(<PatientCheckInPage patientId={7} />);

    expect(await screen.findByRole("checkbox", { name: "Remind me to complete my daily check-in" })).toBeChecked();
    expect(screen.getByLabelText("Reminder time")).toHaveValue("08:00");
    expect(screen.getByText("Timezone: America/New_York")).toBeInTheDocument();
    expect(screen.getByText("CareVoice uses your profile timezone for reminder scheduling.")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Timezone" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "Remind me to complete my daily check-in" }));
    fireEvent.change(screen.getByLabelText("Reminder time"), { target: { value: "08:30" } });
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      const saved = vi.mocked(fetch).mock.calls.find(([url, init]) =>
        String(url).endsWith("/api/me/reminder-preference") && init?.method === "PUT");
      expect(saved?.[1]?.body).toBe(JSON.stringify({ enabled: false, reminderTime: "08:30" }));
    });
    expect(await screen.findByText("Saved.")).toBeInTheDocument();
  });

  it("starts today's check-in from an unread reminder and keeps the copy generic", async () => {
    const user = userEvent.setup();
    render(<PatientCheckInPage patientId={7} />);
    const banner = await screen.findByRole("region", { name: "CareVoice reminder" });
    expect(banner).toHaveTextContent("Good morning. Your CareVoice daily check-in is ready.");
    expect(banner).not.toHaveTextContent(/pain|dizzy|medication|Post-operative|transcript|symptom/i);
    await user.click(within(banner).getByRole("button", { name: "Start Check-In" }));
    await screen.findByRole("heading", { name: OPENING });
    expect(vi.mocked(fetch).mock.calls.filter(([url, init]) =>
      String(url).endsWith("/api/me/check-in/today") && (init?.method ?? "GET").toUpperCase() === "POST")).toHaveLength(1);
    for (let index = 0; index < localStorage.length; index += 1) {
      const value = localStorage.getItem(localStorage.key(index) ?? "") ?? "";
      expect(value).not.toContain("Good morning");
      expect(value).not.toContain("Post-operative");
    }
  });

  it("offers continue for a previous check-in and dismisses a reminder without storing it", async () => {
    const user = userEvent.setup();
    todayBody = today("IN_PROGRESS", true);
    notifications = [notification("PREVIOUS_CHECK_IN_CONTINUE", "Your previous CareVoice check-in is still in progress.")];
    render(<PatientCheckInPage patientId={7} />);
    const banner = await screen.findByRole("region", { name: "CareVoice reminder" });
    expect(banner).toHaveTextContent("Your previous CareVoice check-in is still in progress.");
    await user.click(within(banner).getByRole("button", { name: "Continue Check-In" }));
    await screen.findByRole("heading", { name: OPENING });

    cleanup();
    todayBody = today("NOT_STARTED");
    notifications = [notification("DAILY_CHECK_IN_START", "Good morning. Your CareVoice daily check-in is ready.")];
    render(<PatientCheckInPage patientId={7} />);
    const nextBanner = await screen.findByRole("region", { name: "CareVoice reminder" });
    await user.click(within(nextBanner).getByRole("button", { name: "Dismiss" }));
    await waitFor(() => expect(screen.queryByRole("region", { name: "CareVoice reminder" })).not.toBeInTheDocument());
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      expect.stringContaining("/api/me/notifications/101/read"),
      expect.objectContaining({ method: "POST" }),
    );
    for (let index = 0; index < localStorage.length; index += 1) {
      expect(localStorage.getItem(localStorage.key(index) ?? "") ?? "").not.toContain("Good morning");
    }
  });

  it("does not offer another check-in after today is already complete", async () => {
    todayBody = today("COMPLETED");
    render(<PatientCheckInPage patientId={7} />);
    const banner = await screen.findByRole("region", { name: "CareVoice reminder" });
    expect(within(banner).getByRole("button", { name: "Dismiss" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Check-In" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Continue Check-In" })).not.toBeInTheDocument();
  });
});
