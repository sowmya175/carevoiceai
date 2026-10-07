import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import App from "./App.tsx";
import { historySummary, jsonResponse, sessionHistory } from "./test/historyFixtures.ts";

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes("longitudinal-summary")) return jsonResponse(historySummary());
    if (url.endsWith("/history")) return jsonResponse(sessionHistory(3));
    if (url.endsWith("/api/patients")) return jsonResponse({ id: 7 }, 201);
    if (url.endsWith("/sessions")) return jsonResponse({ sessionId: 3, status: "IN_PROGRESS", nextQuestion: "How are you feeling?" });
    if (url.endsWith("/messages")) return jsonResponse({ sessionId: 3, status: "COMPLETED", riskLevel: "GREEN", conversationComplete: true, nextQuestion: null });
    throw new Error("Unexpected request");
  }));
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("preserves an unsent text draft when navigating to history and back", async () => {
  const user = userEvent.setup();
  render(<App />);
  await user.click(screen.getByRole("button", { name: "Start Today's Check-In" }));
  await screen.findByRole("heading", { name: "How are you feeling?" });
  // Voice is unsupported in jsdom, so the text form opens automatically.
  await user.type(await screen.findByLabelText("Type your answer"), "An unsent private draft");
  await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: "History" }));
  await screen.findByRole("region", { name: "Recent check-ins" });
  await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: "Today's Check-In" }));
  expect(screen.getByLabelText("Type your answer")).toHaveValue("An unsent private draft");
  expect(localStorage.getItem("carevoice.checkin")).not.toContain("private draft");
  expect(vi.mocked(fetch).mock.calls.filter(([url]) => String(url).endsWith("/sessions"))).toHaveLength(1);
});

it("connects completion to today's responses and health history while storing identifiers only", async () => {
  const user = userEvent.setup();
  const writes = vi.spyOn(Storage.prototype, "setItem");
  render(<App />);
  await user.click(screen.getByRole("button", { name: "Start Today's Check-In" }));
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
    expect(JSON.parse(value)).toEqual({ patientId: 7, sessionId: 3 });
  }
});

it("uses the persisted patient identifier on reload", async () => {
  localStorage.setItem("carevoice.checkin", JSON.stringify({ patientId: 7, sessionId: 3 }));
  const user = userEvent.setup();
  render(<App />);
  await screen.findByText("Your responses have been saved.");
  await user.click(within(screen.getByRole("navigation")).getByRole("button", { name: "History" }));
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/patients/7/longitudinal-summary?limit=30"), expect.anything()));
  expect(vi.mocked(fetch).mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
});
