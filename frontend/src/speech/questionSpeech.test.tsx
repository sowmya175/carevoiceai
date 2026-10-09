import { StrictMode, type ReactElement } from "react";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App.tsx";
import { PatientCheckInPage } from "../pages/PatientCheckInPage.tsx";
import { autoReadPreferenceKey } from "./autoReadPreference.ts";
import { resetAutoReadQuestion } from "./questionSpeech.ts";
import { historySummary, jsonResponse, sessionHistory } from "../test/historyFixtures.ts";

const OPENING = "Tell me how you are feeling today in your own words.";
const NEXT = "Have you taken your prescribed medication today?";
const TRANSCRIPT = "I feel dizzy and my pain is around six.";

const recorder = vi.hoisted(() => ({
  state: "idle" as "idle" | "recording" | "recorded" | "uploading" | "error",
  elapsedSeconds: 0,
  blob: null as Blob | null,
  errorMessage: null as string | null,
  supported: true,
  startRecording: vi.fn(async () => {}),
  stopRecording: vi.fn(),
  clearRecording: vi.fn(),
  beginUpload: vi.fn(),
  endUpload: vi.fn(),
}));

vi.mock("../hooks/useVoiceRecorder.ts", () => ({
  useVoiceRecorder: () => recorder,
}));

class MockUtterance {
  text: string;
  lang = "";
  rate = 0;
  pitch = 0;
  volume = 0;
  voice: { lang: string; name: string } | null = null;
  onstart: (() => void) | null = null;
  onend: (() => void) | null = null;
  onerror: ((event: { error: string }) => void) | null = null;

  constructor(text: string) {
    this.text = text;
  }
}

const english = { lang: "en-US", name: "Test English" };
const french = { lang: "fr-FR", name: "Test French" };
const utterances: MockUtterance[] = [];
const order: string[] = [];
const speak = vi.fn((utterance: MockUtterance) => {
  utterances.push(utterance);
  order.push("speak");
  utterance.onstart?.();
});
const cancel = vi.fn(() => {
  order.push("cancel");
});

function installSpeech() {
  vi.stubGlobal("SpeechSynthesisUtterance", MockUtterance);
  vi.stubGlobal("speechSynthesis", {
    speak,
    cancel,
    getVoices: () => [french, english],
  });
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  localStorage.clear();
  resetAutoReadQuestion();
  utterances.length = 0;
  order.length = 0;
  speak.mockClear();
  cancel.mockClear();
  recorder.state = "idle";
  recorder.blob = null;
  recorder.startRecording.mockClear();
  installSpeech();
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith("/api/auth/csrf")) return json({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    if (url.endsWith("/api/auth/me")) {
      return json({
        authenticated: true, accountId: 1, username: "ada01", role: "PATIENT",
        patient: { id: 7, fullName: "Ada Lovelace", medicalCondition: "Recovery", timezone: "America/New_York" },
      });
    }
    if (url.endsWith("/api/auth/logout")) return json({});
    if (url.endsWith("/api/me/monitoring-plan")) {
      return json({ name: "General Daily Wellness", description: "Demo monitoring plan. Not clinically validated." });
    }
    if (url.includes("longitudinal-summary")) return jsonResponse(historySummary());
    if (url.endsWith("/history")) return jsonResponse(sessionHistory(3));
    if (url.endsWith("/api/me/check-in/today") && (init?.method ?? "GET").toUpperCase() === "GET") {
      return json({
        checkInDate: "2026-10-08", currentDate: "2026-10-08", timezone: "America/New_York",
        status: "NOT_STARTED", sessionId: null, monitoringPlanName: "General Daily Wellness",
        startedAt: null, completedAt: null, previousDaySession: false,
      });
    }
    if (url.endsWith("/api/me/check-in/today")) {
      return json({ sessionId: 3, status: "IN_PROGRESS", nextQuestion: OPENING });
    }
    if (url.endsWith("/messages")) {
      return json({
        sessionId: 3, nextQuestion: NEXT, status: "IN_PROGRESS", conversationComplete: false,
      });
    }
    return json({ error: "missing" }, 500);
  }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

async function startCheckIn(ui: ReactElement = <PatientCheckInPage patientId={7} />) {
  const user = userEvent.setup();
  const view = render(ui);
  await user.click(await screen.findByRole("button", { name: "Start Check-In" }));
  await screen.findByRole("heading", { name: OPENING });
  return { user, ...view };
}

describe("question speech", () => {
  it("reads the displayed question once, then stops and reads it again", async () => {
    const { user } = await startCheckIn();
    const read = await screen.findByRole("button", { name: "Read CareVoice question aloud" });
    expect(read).toHaveTextContent("Read aloud");
    expect(read).toBeEnabled();
    await user.click(read);

    expect(speak).toHaveBeenCalledTimes(1);
    expect(utterances[0].text).toBe(OPENING);
    expect(utterances[0].lang).toBe("en-US");
    expect(utterances[0].rate).toBe(1);
    expect(utterances[0].pitch).toBe(1);
    expect(utterances[0].volume).toBe(1);
    expect(utterances[0].voice).toBe(english);
    expect(order[order.lastIndexOf("speak") - 1]).toBe("cancel");

    await user.click(screen.getByRole("button", { name: "Stop reading the question" }));
    expect(cancel).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Read CareVoice question aloud" })).toHaveTextContent("Read again");

    await user.click(screen.getByRole("button", { name: "Read CareVoice question aloud" }));
    expect(speak).toHaveBeenCalledTimes(2);
    expect(utterances[1].text).toBe(OPENING);
    expect(screen.getByRole("heading", { name: OPENING })).toBeInTheDocument();
    expect(vi.mocked(fetch).mock.calls.some((call) => String(call[0]).endsWith("/messages"))).toBe(false);
  });

  it("does not auto-read until the patient turns it on, and then speaks each new question once", async () => {
    const { user, rerender } = await startCheckIn();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(speak).not.toHaveBeenCalled();

    rerender(<PatientCheckInPage patientId={7} />);
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(speak).not.toHaveBeenCalled();

    await user.click(screen.getByRole("checkbox", { name: "Auto-read questions" }));
    await waitFor(() => expect(speak).toHaveBeenCalledTimes(1));
    expect(utterances[0].text).toBe(OPENING);

    rerender(<PatientCheckInPage patientId={7} />);
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(speak).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "Use text instead" }));
    await user.type(screen.getByLabelText("Type your answer"), "I slept well.");
    await user.click(screen.getByRole("button", { name: "Send answer" }));
    await screen.findByRole("heading", { name: NEXT });
    await waitFor(() => expect(utterances.map((item) => item.text)).toEqual([OPENING, NEXT]));
    expect(speak).toHaveBeenCalledTimes(2);
    expect(cancel).toHaveBeenCalled();
  });

  it("speaks a restored question once under Strict Mode when auto-read is on", async () => {
    localStorage.setItem(autoReadPreferenceKey(), "true");
    const view = await startCheckIn(<StrictMode><PatientCheckInPage patientId={7} /></StrictMode>);
    await waitFor(() => expect(speak).toHaveBeenCalledTimes(1));
    expect(utterances[0].text).toBe(OPENING);
    view.rerender(<StrictMode><PatientCheckInPage patientId={7} /></StrictMode>);
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(speak).toHaveBeenCalledTimes(1);
  });

  it("stops speech before microphone recording and does not speak the transcript", async () => {
    const { user, rerender } = await startCheckIn();
    await user.click(await screen.findByRole("button", { name: "Read CareVoice question aloud" }));
    recorder.startRecording.mockImplementation(async () => {
      order.push("record");
    });
    await user.click(screen.getByRole("button", { name: "Record Answer" }));
    expect(order.at(-2)).toBe("cancel");
    expect(order.at(-1)).toBe("record");

    const spoken = speak.mock.calls.length;
    recorder.state = "recorded";
    recorder.blob = new Blob(["voice"], { type: "audio/webm" });
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/voice/transcribe")) return json({ transcript: TRANSCRIPT });
      if (url.endsWith("/api/auth/csrf")) return json({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
      return json({ error: "missing" }, 500);
    });
    rerender(<PatientCheckInPage patientId={7} />);
    expect(await screen.findByText(`"${TRANSCRIPT}"`)).toBeInTheDocument();
    expect(speak).toHaveBeenCalledTimes(spoken);
    expect(utterances.some((item) => item.text.includes("dizzy"))).toBe(false);
  });

  it("cancels speech when the check-in is complete and removes the read control", async () => {
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/csrf")) return json({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
      if (url.endsWith("/api/me/check-in/today") && (init?.method ?? "GET").toUpperCase() !== "POST") {
        return json({
          checkInDate: "2026-10-08", currentDate: "2026-10-08", timezone: "America/New_York",
          status: "NOT_STARTED", sessionId: null, monitoringPlanName: "General Daily Wellness",
          startedAt: null, completedAt: null, previousDaySession: false,
        });
      }
      if (url.endsWith("/api/me/check-in/today")) return json({ sessionId: 3, status: "IN_PROGRESS", nextQuestion: OPENING });
      if (url.endsWith("/messages")) {
        return json({ sessionId: 3, nextQuestion: null, status: "COMPLETED", conversationComplete: true });
      }
      return json({ error: "missing" }, 500);
    });
    const { user } = await startCheckIn();
    await user.click(await screen.findByRole("button", { name: "Read CareVoice question aloud" }));
    const cancels = cancel.mock.calls.length;
    await user.click(screen.getByRole("button", { name: "Use text instead" }));
    await user.type(screen.getByLabelText("Type your answer"), "I feel fine.");
    await user.click(screen.getByRole("button", { name: "Send answer" }));
    expect(await screen.findByRole("heading", { name: "Today's check-in is complete." })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Read CareVoice question aloud" })).not.toBeInTheDocument();
    expect(cancel.mock.calls.length).toBeGreaterThan(cancels);
  });

  it("keeps check-in usable when speech synthesis is missing", async () => {
    vi.stubGlobal("speechSynthesis", undefined);
    vi.stubGlobal("SpeechSynthesisUtterance", undefined);
    const { user } = await startCheckIn();
    expect(await screen.findByText("Read aloud is not supported by this browser.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Read CareVoice question aloud" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Use text instead" }));
    await user.type(screen.getByLabelText("Type your answer"), "I slept well.");
    await user.click(screen.getByRole("button", { name: "Send answer" }));
    expect(await screen.findByRole("heading", { name: NEXT })).toBeInTheDocument();
    expect(speak).not.toHaveBeenCalled();
  });

  it("stores only the auto-read preference and restores it", async () => {
    const { user, unmount } = await startCheckIn();
    await user.click(screen.getByRole("checkbox", { name: "Auto-read questions" }));
    expect(localStorage.getItem(autoReadPreferenceKey())).toBe("true");
    expect(JSON.stringify(localStorage)).not.toContain(OPENING);
    expect(JSON.stringify(localStorage)).not.toContain("Ada");
    unmount();
    render(<PatientCheckInPage patientId={7} />);
    await user.click(await screen.findByRole("button", { name: "Start Check-In" }));
    await screen.findByRole("heading", { name: OPENING });
    expect(screen.getByRole("checkbox", { name: "Auto-read questions" })).toBeChecked();
  });

  it("shows a non-technical message when speech fails and allows another try", async () => {
    const { user } = await startCheckIn();
    await user.click(await screen.findByRole("button", { name: "Read CareVoice question aloud" }));
    utterances[0].onerror?.({ error: "synthesis-unavailable" });
    expect(await screen.findByText("Read aloud didn't start. You can try again.")).toBeInTheDocument();
    expect(screen.queryByText("synthesis-unavailable")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Read CareVoice question aloud" }));
    expect(speak).toHaveBeenCalledTimes(2);
  });

  it("cancels speech when the patient opens history or signs out", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByRole("button", { name: "Start Check-In" }));
    await user.click(await screen.findByRole("button", { name: "Read CareVoice question aloud" }));
    const afterRead = cancel.mock.calls.length;
    await user.click(within(screen.getByRole("navigation", { name: "Patient navigation" })).getByRole("button", { name: "History" }));
    await waitFor(() => expect(cancel.mock.calls.length).toBeGreaterThan(afterRead));
    await user.click(screen.getByRole("button", { name: "Today's Check-In" }));
    await user.click(await screen.findByRole("button", { name: "Read CareVoice question aloud" }));
    const afterReturn = cancel.mock.calls.length;
    await user.click(screen.getByRole("button", { name: "Sign Out" }));
    await screen.findByRole("button", { name: "Patient Login" });
    expect(cancel.mock.calls.length).toBeGreaterThan(afterReturn);
  });
});
