import type { ReactElement } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PatientCheckInPage } from "./PatientCheckInPage.tsx";

const recorder = vi.hoisted(() => ({
  state: "idle" as "idle" | "recording" | "recorded" | "uploading" | "error",
  elapsedSeconds: 0,
  blob: null as Blob | null,
  errorMessage: null as string | null,
  supported: true,
  startRecording: vi.fn(),
  stopRecording: vi.fn(),
  clearRecording: vi.fn(() => {
    recorder.state = "idle";
    recorder.blob = null;
    recorder.errorMessage = null;
  }),
  beginUpload: vi.fn(),
  endUpload: vi.fn(),
}));

vi.mock("../hooks/useVoiceRecorder.ts", () => ({
  useVoiceRecorder: () => recorder,
}));

let voiceStatus = 200;
let todayBody: Record<string, unknown> = notStarted();
let voiceTranscript = "I feel dizzy and my pain is around six.";
let voiceAgent: Record<string, unknown> = {
  sessionId: 3,
  nextQuestion: "When did the dizziness start?",
  requestedField: "DIZZINESS_ONSET",
  missingFields: ["DIZZINESS_ONSET"],
  riskLevel: "YELLOW",
  status: "IN_PROGRESS",
  conversationComplete: false,
  collectedFacts: {},
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  localStorage.clear();
  voiceStatus = 200;
  todayBody = notStarted();
  voiceTranscript = "I feel dizzy and my pain is around six.";
  voiceAgent = {
    sessionId: 3,
    nextQuestion: "When did the dizziness start?",
    requestedField: "DIZZINESS_ONSET",
    missingFields: ["DIZZINESS_ONSET"],
    riskLevel: "YELLOW",
    status: "IN_PROGRESS",
    conversationComplete: false,
    collectedFacts: {},
  };
  recorder.state = "idle";
  recorder.blob = null;
  recorder.supported = true;
  recorder.errorMessage = null;
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith("/api/auth/csrf")) {
      return json({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    }
    if (url.endsWith("/api/patients")) {
      return json({ id: 7 }, 201);
    }
    if (url.endsWith("/api/me/check-in/today")) {
      if ((init?.method ?? "GET").toUpperCase() === "POST") {
        return json({
          sessionId: 3,
          status: "IN_PROGRESS",
          riskLevel: "GREEN",
          nextQuestion: "Tell me how you are feeling today in your own words.",
        });
      }
      return json(todayBody);
    }
    if (url.endsWith("/voice/transcribe")) {
      return json(voiceStatus === 200 ? { transcript: voiceTranscript } : voiceBodyForStatus(), voiceStatus);
    }
    if (url.endsWith("/messages")) {
      const payload = typeof init?.body === "string" ? JSON.parse(init.body) as { inputMode?: string } : {};
      if (payload.inputMode === "VOICE") {
        return json(voiceAgent);
      }
      return json({
        sessionId: 3,
        nextQuestion: "Have you taken your prescribed medication today?",
        requestedField: "MEDICATION_TAKEN",
        missingFields: [],
        riskLevel: "YELLOW",
        status: "IN_PROGRESS",
        conversationComplete: false,
        collectedFacts: {},
      });
    }
    return json({ error: "missing" }, 500);
  }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

async function startCheckIn() {
  const user = userEvent.setup();
  const view = render(<PatientCheckInPage patientId={7} />);
  await user.click(await screen.findByRole("button", { name: "Start Check-In" }));
  await screen.findByRole("heading", { name: "Tell me how you are feeling today in your own words." });
  return { user, rerender: view.rerender };
}

function prepareRecording() {
  recorder.state = "recorded";
  recorder.blob = new Blob([`voice-${crypto.randomUUID()}`], { type: "audio/webm;codecs=opus" });
}

function voiceBodyForStatus(): { error: string } {
  if (voiceStatus === 422) {
    return { error: "No understandable speech was detected. Please try recording again or use text input." };
  }
  if (voiceStatus === 400) {
    return { error: "Unsupported audio format." };
  }
  return { error: "provider body" };
}

async function reviewRecording(rerender: (ui: ReactElement) => void) {
  prepareRecording();
  rerender(<PatientCheckInPage patientId={7} />);
  expect(await screen.findByText(`"${voiceTranscript}"`)).toBeInTheDocument();
}

describe("PatientCheckInPage", () => {
  it("starts a check-in and shows the backend question", async () => {
    await startCheckIn();
    expect(await screen.findByRole("heading", { name: "Tell me how you are feeling today in your own words." })).toBeInTheDocument();
    expect(screen.queryByText("GREEN")).not.toBeInTheDocument();
    expect(screen.queryByText("DIZZINESS_ONSET")).not.toBeInTheDocument();
  });

  it("shows the transcript and the next question after a voice answer", async () => {
    const { user, rerender } = await startCheckIn();
    await reviewRecording(rerender);
    const fetchMock = vi.mocked(fetch);
    expect(fetchMock.mock.calls.some((call) => String(call[0]).endsWith("/messages"))).toBe(false);
    expect(screen.getByText("We heard:")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Use this answer" }));

    expect(await screen.findByText("I feel dizzy and my pain is around six.")).toBeInTheDocument();
    expect(screen.getAllByText("When did the dizziness start?").length).toBeGreaterThan(0);
    expect(screen.queryByText("YELLOW")).not.toBeInTheDocument();

    const voiceCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/voice/transcribe"));
    expect(voiceCall).toBeTruthy();
    const init = voiceCall?.[1];
    expect(init?.body).toBeInstanceOf(FormData);
    expect((init?.body as FormData).get("audio")).toBeTruthy();
    expect(init?.headers).toMatchObject({ Accept: "application/json", "X-CSRF-TOKEN": "csrf-test" });
    expect(init?.headers).not.toHaveProperty("Content-Type");
    const confirmCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/messages"));
    expect(confirmCall?.[1]?.body).toBe(JSON.stringify({
      message: "I feel dizzy and my pain is around six.",
      inputMode: "VOICE",
    }));
    await waitFor(() => {
      const stored = localStorage.getItem("carevoice.checkin") ?? "";
      expect(stored).toContain("\"sessionId\":3");
      expect(stored).not.toContain("I feel dizzy");
      expect(stored).not.toContain("transcript");
    });
  });

  it("hides the recorder when the check-in is complete", async () => {
    voiceTranscript = "No, I did not faint.";
    voiceAgent = {
      sessionId: 3,
      nextQuestion: null,
      requestedField: null,
      missingFields: [],
      riskLevel: "GREEN",
      status: "COMPLETED",
      conversationComplete: true,
      collectedFacts: {},
    };
    const { user, rerender } = await startCheckIn();
    await reviewRecording(rerender);
    await user.click(screen.getByRole("button", { name: "Use this answer" }));

    expect(await screen.findByRole("heading", { name: "Today's check-in is complete." })).toBeInTheDocument();
    expect(screen.getByText("Your daily check-in has been completed.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Record Answer" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Use this answer" })).not.toBeInTheDocument();
  });

  it("uses neutral wording when the check-in is ready for review", async () => {
    voiceTranscript = "I have been short of breath.";
    voiceAgent = {
      sessionId: 3,
      nextQuestion: null,
      requestedField: null,
      missingFields: [],
      riskLevel: "RED",
      status: "READY_FOR_REVIEW",
      conversationComplete: true,
      collectedFacts: {},
    };
    const { user, rerender } = await startCheckIn();
    await reviewRecording(rerender);
    await user.click(screen.getByRole("button", { name: "Use this answer" }));

    expect(await screen.findByText("Your check-in has been recorded for review by your care team.")).toBeInTheDocument();
    expect(screen.queryByText("RED")).not.toBeInTheDocument();
  });

  it("sends a typed answer to the text endpoint", async () => {
    const { user } = await startCheckIn();
    await user.click(screen.getByRole("button", { name: "Use text instead" }));
    await user.type(screen.getByLabelText("Type your answer"), "I slept well.");
    await user.click(screen.getByRole("button", { name: "Send answer" }));

    expect(await screen.findByRole("heading", { name: "Have you taken your prescribed medication today?" })).toBeInTheDocument();
    const fetchMock = vi.mocked(fetch);
    const textCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/messages"));
    expect(textCall?.[1]?.body).toBe(JSON.stringify({ message: "I slept well." }));
    expect(screen.getByText("I slept well.")).toBeInTheDocument();
    await waitFor(() => {
      const stored = localStorage.getItem("carevoice.checkin") ?? "";
      expect(stored).toContain("\"sessionId\":3");
      expect(stored).not.toContain("I slept well");
      expect(stored).not.toContain("clinicalNote");
      expect(stored).not.toContain("YELLOW");
      expect(stored).not.toContain("turns");
    });
  });

  it("restores the check-in from server history and ignores stored conversation text", async () => {
    localStorage.setItem("carevoice.checkin", JSON.stringify({
      patientId: 7,
      sessionId: 3,
      turns: [{ text: "SECRET TRANSCRIPT" }],
      riskLevel: "RED",
      currentQuestion: "Stored question that must not be used",
    }));
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/me/check-in/today")) {
        return json({
          ...notStarted(),
          status: "IN_PROGRESS",
          sessionId: 3,
          startedAt: "2026-10-02T15:00:00Z",
        });
      }
      if (url.endsWith("/history")) {
        return json({
          sessionId: 3,
          patientId: 7,
          startedAt: "2026-10-02T15:00:00Z",
          status: "IN_PROGRESS",
          currentQuestion: "When did the dizziness start?",
          conversationComplete: false,
          turns: [{
            sequenceNumber: 1,
            timestamp: "2026-10-02T15:01:00Z",
            question: "Tell me how you are feeling today in your own words.",
            patientResponse: "Recovered from the server.",
            inputMode: "VOICE",
            clinicalNote: "Patient reports dizziness.",
            riskLevel: "YELLOW",
          }],
        });
      }
      return json({ error: "missing" }, 500);
    });

    render(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByText("Recovered from the server.")).toBeInTheDocument();
    expect(screen.getByText("We heard:")).toBeInTheDocument();
    expect(screen.queryByText("SECRET TRANSCRIPT")).not.toBeInTheDocument();
    expect(screen.queryByText("Patient reports dizziness.")).not.toBeInTheDocument();
    expect(screen.queryByText("YELLOW")).not.toBeInTheDocument();
    const stored = localStorage.getItem("carevoice.checkin") ?? "";
    expect(stored).not.toContain("SECRET TRANSCRIPT");
    expect(stored).not.toContain("Recovered from the server");
  });

  it("lets the patient edit the transcript before it is saved", async () => {
    const { user, rerender } = await startCheckIn();
    await reviewRecording(rerender);
    await user.click(screen.getByRole("button", { name: "Edit" }));
    const editor = screen.getByLabelText("Edit transcript");
    await user.clear(editor);
    await user.type(editor, "My pain is five.");
    await user.click(screen.getByRole("button", { name: "Use this answer" }));

    expect(await screen.findByText("My pain is five.")).toBeInTheDocument();
    expect(screen.queryByText("I feel dizzy and my pain is around six.")).not.toBeInTheDocument();
    const confirmCall = vi.mocked(fetch).mock.calls.find((call) => String(call[0]).endsWith("/messages"));
    expect(confirmCall?.[1]?.body).toBe(JSON.stringify({
      message: "My pain is five.",
      inputMode: "VOICE",
    }));
  });

  it("discards an unconfirmed transcript when the patient records again", async () => {
    const { user, rerender } = await startCheckIn();
    await reviewRecording(rerender);
    const callsBefore = vi.mocked(fetch).mock.calls.length;
    await user.click(screen.getByRole("button", { name: "Record again" }));

    expect(screen.queryByText(`"${voiceTranscript}"`)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Record Answer" })).toBeInTheDocument();
    expect(recorder.clearRecording).toHaveBeenCalled();
    expect(vi.mocked(fetch).mock.calls.slice(callsBefore).some((call) => String(call[0]).endsWith("/messages"))).toBe(false);
  });

  it("shows patient-friendly voice errors", async () => {
    voiceStatus = 422;
    const { rerender } = await startCheckIn();
    prepareRecording();
    rerender(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("We couldn't understand the recording. Please try again.");

    voiceStatus = 502;
    prepareRecording();
    rerender(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Voice processing is temporarily unavailable. Please try again or use text input.",
    );

    voiceStatus = 400;
    prepareRecording();
    rerender(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Unsupported audio format.");
    expect(vi.mocked(fetch).mock.calls.some((call) => String(call[0]).endsWith("/messages"))).toBe(false);
  });

  it("shows start, continue, completed, and review states from today's status", async () => {
    const { rerender } = render(<PatientCheckInPage patientId={7} onViewResponses={vi.fn()} />);
    expect(await screen.findByRole("button", { name: "Start Check-In" })).toBeInTheDocument();
    expect(screen.getByText("Status: Not started")).toBeInTheDocument();
    expect(screen.getByText("October 8, 2026")).toBeInTheDocument();

    todayBody = { ...notStarted(), status: "IN_PROGRESS", sessionId: 3, startedAt: "2026-10-08T14:00:00Z" };
    rerender(<PatientCheckInPage patientId={8} />);
    expect(await screen.findByRole("button", { name: "Continue Check-In" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Check-In" })).not.toBeInTheDocument();

    const onView = vi.fn();
    todayBody = { ...notStarted(), status: "COMPLETED", sessionId: 3, startedAt: "2026-10-08T14:00:00Z", completedAt: "2026-10-08T14:20:00Z" };
    rerender(<PatientCheckInPage patientId={9} onViewResponses={onView} />);
    expect(await screen.findByText("✓ Today's check-in is complete")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Start/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "View Today's Responses" }));
    expect(onView).toHaveBeenCalledWith(3);

    todayBody = { ...notStarted(), status: "READY_FOR_REVIEW", sessionId: 3, startedAt: "2026-10-08T14:00:00Z", completedAt: "2026-10-08T14:20:00Z" };
    rerender(<PatientCheckInPage patientId={10} onViewResponses={vi.fn()} />);
    expect(await screen.findByText("Today's check-in was submitted for review.")).toBeInTheDocument();
    expect(screen.getByText("Status: Ready for review")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Check-In" })).not.toBeInTheDocument();
  });

  it("offers to finish a previous day's unfinished check-in", async () => {
    todayBody = {
      ...notStarted(),
      status: "IN_PROGRESS",
      sessionId: 3,
      checkInDate: "2026-10-08",
      currentDate: "2026-10-09",
      previousDaySession: true,
      startedAt: "2026-10-09T03:58:00Z",
    };
    render(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByText(/Finish your previous check-in before starting today's check-in/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue previous check-in" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start Check-In" })).not.toBeInTheDocument();
  });

  it("discards a stored session id when today's check-in is a different session", async () => {
    localStorage.setItem("carevoice.checkin", JSON.stringify({
      sessionId: 3,
      turns: [{ text: "SECRET TRANSCRIPT" }],
    }));
    todayBody = { ...notStarted(), status: "IN_PROGRESS", sessionId: 9, startedAt: "2026-10-08T14:00:00Z" };
    render(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByRole("button", { name: "Continue Check-In" })).toBeInTheDocument();
    expect(screen.queryByText("SECRET TRANSCRIPT")).not.toBeInTheDocument();
    expect(localStorage.getItem("carevoice.checkin")).toBeNull();
  });

  it("continues the original same-day session instead of starting another", async () => {
    todayBody = {
      ...notStarted(),
      status: "IN_PROGRESS",
      sessionId: 3,
      monitoringPlanName: "General Daily Wellness",
      startedAt: "2026-10-08T14:00:00Z",
    };
    const user = userEvent.setup();
    render(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByText("Monitoring Plan: General Daily Wellness")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Continue Check-In" }));
    expect(await screen.findByRole("heading", { name: "Tell me how you are feeling today in your own words." })).toBeInTheDocument();
    const starts = vi.mocked(fetch).mock.calls.filter((call) =>
      String(call[0]).endsWith("/api/me/check-in/today") && (call[1]?.method ?? "GET").toUpperCase() === "POST");
    expect(starts).toHaveLength(1);
    expect(vi.mocked(fetch).mock.calls.some((call) => String(call[0]).includes("/patients/7/sessions"))).toBe(false);
  });
});

function notStarted(): Record<string, unknown> {
  return {
    checkInDate: "2026-10-08",
    currentDate: "2026-10-08",
    timezone: "America/New_York",
    status: "NOT_STARTED",
    sessionId: null,
    monitoringPlanName: "Post-Operative Recovery Demo",
    startedAt: null,
    completedAt: null,
    previousDaySession: false,
  };
}
