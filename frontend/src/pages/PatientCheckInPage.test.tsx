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
  clearRecording: vi.fn(),
  beginUpload: vi.fn(),
  endUpload: vi.fn(),
}));

vi.mock("../hooks/useVoiceRecorder.ts", () => ({
  useVoiceRecorder: () => recorder,
}));

let voiceStatus = 200;
let voiceBody: unknown = {
  transcript: "I feel dizzy and my pain is around six.",
  agentResponse: {
    sessionId: 3,
    nextQuestion: "When did the dizziness start?",
    requestedField: "DIZZINESS_ONSET",
    missingFields: ["DIZZINESS_ONSET"],
    riskLevel: "YELLOW",
    status: "IN_PROGRESS",
    conversationComplete: false,
    collectedFacts: {},
  },
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
  voiceBody = {
    transcript: "I feel dizzy and my pain is around six.",
    agentResponse: {
      sessionId: 3,
      nextQuestion: "When did the dizziness start?",
      requestedField: "DIZZINESS_ONSET",
      missingFields: ["DIZZINESS_ONSET"],
      riskLevel: "YELLOW",
      status: "IN_PROGRESS",
      conversationComplete: false,
      collectedFacts: {},
    },
  };
  recorder.state = "idle";
  recorder.blob = null;
  recorder.supported = true;
  recorder.errorMessage = null;
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith("/api/patients")) {
      return json({ id: 7 }, 201);
    }
    if (url.endsWith("/sessions")) {
      return json({
        sessionId: 3,
        status: "IN_PROGRESS",
        riskLevel: "GREEN",
        nextQuestion: "Tell me how you are feeling today in your own words.",
      });
    }
    if (url.endsWith("/voice")) {
      return json(voiceBody, voiceStatus);
    }
    if (url.endsWith("/messages")) {
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
  const view = render(<PatientCheckInPage />);
  await user.click(screen.getByRole("button", { name: "Start Today's Check-In" }));
  await screen.findByRole("heading", { name: "Tell me how you are feeling today in your own words." });
  return { user, rerender: view.rerender };
}

function prepareRecording() {
  recorder.state = "recorded";
  recorder.blob = new Blob(["voice"], { type: "audio/webm;codecs=opus" });
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
    prepareRecording();
    rerender(<PatientCheckInPage />);
    await user.click(screen.getByRole("button", { name: "Submit Answer" }));

    expect(await screen.findByText("I feel dizzy and my pain is around six.")).toBeInTheDocument();
    expect(screen.getByText("We heard:")).toBeInTheDocument();
    expect(screen.getAllByText("When did the dizziness start?").length).toBeGreaterThan(0);
    expect(screen.queryByText("YELLOW")).not.toBeInTheDocument();

    const fetchMock = vi.mocked(fetch);
    const voiceCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/voice"));
    expect(voiceCall).toBeTruthy();
    const init = voiceCall?.[1];
    expect(init?.body).toBeInstanceOf(FormData);
    expect((init?.body as FormData).get("audio")).toBeTruthy();
    expect(init?.headers).toEqual({ Accept: "application/json" });
  });

  it("hides the recorder when the check-in is complete", async () => {
    voiceBody = {
      transcript: "No, I did not faint.",
      agentResponse: {
        sessionId: 3,
        nextQuestion: null,
        requestedField: null,
        missingFields: [],
        riskLevel: "GREEN",
        status: "COMPLETED",
        conversationComplete: true,
        collectedFacts: {},
      },
    };
    const { user, rerender } = await startCheckIn();
    prepareRecording();
    rerender(<PatientCheckInPage />);
    await user.click(screen.getByRole("button", { name: "Submit Answer" }));

    expect(await screen.findByRole("heading", { name: "Today's check-in is complete." })).toBeInTheDocument();
    expect(screen.getByText("Your daily check-in has been completed.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Record Answer" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Submit Answer" })).not.toBeInTheDocument();
  });

  it("uses neutral wording when the check-in is ready for review", async () => {
    voiceBody = {
      transcript: "I have been short of breath.",
      agentResponse: {
        sessionId: 3,
        nextQuestion: null,
        requestedField: null,
        missingFields: [],
        riskLevel: "RED",
        status: "READY_FOR_REVIEW",
        conversationComplete: true,
        collectedFacts: {},
      },
    };
    const { user, rerender } = await startCheckIn();
    prepareRecording();
    rerender(<PatientCheckInPage />);
    await user.click(screen.getByRole("button", { name: "Submit Answer" }));

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

    render(<PatientCheckInPage />);
    expect(await screen.findByText("Recovered from the server.")).toBeInTheDocument();
    expect(screen.getByText("We heard:")).toBeInTheDocument();
    expect(screen.queryByText("SECRET TRANSCRIPT")).not.toBeInTheDocument();
    expect(screen.queryByText("Patient reports dizziness.")).not.toBeInTheDocument();
    expect(screen.queryByText("YELLOW")).not.toBeInTheDocument();
    const stored = localStorage.getItem("carevoice.checkin") ?? "";
    expect(stored).not.toContain("SECRET TRANSCRIPT");
    expect(stored).not.toContain("Recovered from the server");
  });

  it("shows patient-friendly voice errors", async () => {
    voiceStatus = 422;
    voiceBody = { error: "No understandable speech was detected. Please try recording again or use text input." };
    const { user, rerender } = await startCheckIn();
    prepareRecording();
    rerender(<PatientCheckInPage />);
    await user.click(screen.getByRole("button", { name: "Submit Answer" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("We couldn't understand the recording. Please try again.");

    voiceStatus = 502;
    voiceBody = { error: "provider body" };
    await user.click(screen.getByRole("button", { name: "Submit Answer" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Voice processing is temporarily unavailable. Please try again or use text input.",
    );

    voiceStatus = 400;
    voiceBody = { error: "Unsupported audio format." };
    await user.click(screen.getByRole("button", { name: "Submit Answer" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Unsupported audio format.");
  });
});
