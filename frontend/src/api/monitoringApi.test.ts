import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchLongitudinalSummary, fetchSessionHistory, messageForFailedResponse, resetCsrf, sendTextMessage } from "./monitoringApi.ts";
import { historySummary, jsonResponse, sessionHistory } from "../test/historyFixtures.ts";

afterEach(() => vi.unstubAllGlobals());

describe("history API", () => {
  it("requests the bounded summary and disables caching", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(historySummary())));
    const signal = new AbortController().signal;
    expect((await fetchLongitudinalSummary(7, signal)).medication.unknownCount).toBe(1);
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/patients/7/longitudinal-summary?limit=30"),
      { headers: { Accept: "application/json" }, cache: "no-store", credentials: "include", signal });
  });

  it("rejects partial, invalid and wrong-patient summaries instead of inventing values", async () => {
    const invalid = historySummary(); invalid.pain.observations[0].value = 99;
    for (const body of [{ patientId: 7 }, { ...historySummary(), medication: null }, { ...historySummary(), patientId: 99 }, invalid]) {
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(body)));
      await expect(fetchLongitudinalSummary(7)).rejects.toThrow("We couldn't load your health history");
    }
  });

  it("does not expose a server error body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ error: "SECRET stack trace" }, 500)));
    await expect(fetchLongitudinalSummary(7)).rejects.toThrow("We couldn't load your health history. Please try again.");
  });

  it("rejects malformed detailed history and disables caching", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ ...sessionHistory(), turns: [null] })));
    await expect(fetchSessionHistory(103)).rejects.toThrow();
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/monitoring/sessions/103/history"), expect.objectContaining({ cache: "no-store" }));
  });
});

it("sends the session cookie and a memory-only CSRF header", async () => {
  resetCsrf();
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith("/api/auth/csrf")) return jsonResponse({ headerName: "X-CSRF-TOKEN", token: "csrf-test" });
    return jsonResponse({ sessionId: 3, conversationComplete: false, status: "IN_PROGRESS" });
  }));
  await sendTextMessage(3, "hello");
  const calls = vi.mocked(fetch).mock.calls;
  expect(String(calls[0][0])).toContain("/api/auth/csrf");
  expect(calls[0][1]).toMatchObject({ credentials: "include" });
  expect(calls[1][1]).toMatchObject({
    method: "POST",
    credentials: "include",
    headers: expect.objectContaining({ "X-CSRF-TOKEN": "csrf-test" }),
  });
  expect(localStorage.length).toBe(0);
});

describe("messageForFailedResponse", () => {
  it("uses a patient-friendly message for an unclear recording", () => {
    expect(messageForFailedResponse(422, "No understandable speech was detected. Please try recording again or use text input."))
      .toBe("We couldn't understand the recording. Please try again.");
  });

  it("uses a patient-friendly message when voice processing is unavailable", () => {
    expect(messageForFailedResponse(502, "Unable to transcribe this recording. Please try again or use text input."))
      .toBe("Voice processing is temporarily unavailable. Please try again or use text input.");
  });

  it("shows a short backend validation message for HTTP 400", () => {
    expect(messageForFailedResponse(400, "Unsupported audio format.")).toBe("Unsupported audio format.");
  });

  it("hides provider details", () => {
    expect(messageForFailedResponse(400, "Gemini ApiException stack trace")).toBe(
      "Something went wrong. Please try again or use text input.",
    );
  });
});
