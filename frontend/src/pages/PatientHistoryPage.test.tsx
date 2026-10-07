import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PatientHistoryPage } from "./PatientHistoryPage.tsx";
import { emptySummary, historySummary, jsonResponse, sessionHistory } from "../test/historyFixtures.ts";

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => String(input).includes("longitudinal-summary")
    ? jsonResponse(historySummary()) : jsonResponse(sessionHistory())));
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

function page() { return render(<PatientHistoryPage patientId={7} onCheckIn={vi.fn()} />); }

describe("Patient history", () => {
  it("shows loading until the server responds", () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));
    page();
    expect(screen.getByRole("status")).toHaveTextContent("Loading your health history");
  });

  it("shows an empty state with a way back to check-in", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(emptySummary()));
    const onCheckIn = vi.fn();
    render(<PatientHistoryPage patientId={7} onCheckIn={onCheckIn} />);
    expect(await screen.findByText(/No completed check-ins yet/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Go to Today's Check-In" }));
    expect(onCheckIn).toHaveBeenCalledOnce();
  });

  it("does not create a patient or fetch history without a known identifier", () => {
    render(<PatientHistoryPage patientId={null} onCheckIn={vi.fn()} />);
    expect(screen.getByText(/No completed check-ins yet/)).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("renders the overview and pain observations without clinical interpretation", async () => {
    page();
    expect(await screen.findByRole("region", { name: "History overview" })).toHaveTextContent("3 check-ins");
    const pain = within(screen.getByRole("region", { name: "Pain over time" }));
    expect(pain.getAllByText("6/10")[0]).toBeInTheDocument();
    expect(pain.getByText("+4")).toBeInTheDocument();
    expect(pain.getByRole("img")).toHaveAccessibleName(/Pain scores over time/);
    await userEvent.click(pain.getByText("View pain readings (3)"));
    expect(pain.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      expect.stringContaining("6/10"), expect.stringContaining("4/10"), expect.stringContaining("2/10"),
    ]);
    expect(document.body.textContent).not.toMatch(/worsening|improving|high risk/i);
  });

  it("keeps medication unknown separate from missed", async () => {
    page();
    const medication = within(await screen.findByRole("region", { name: "Medication" }));
    expect(medication.getAllByText("Missed")[0].parentElement).toHaveTextContent("Missed1");
    expect(medication.getAllByText("Unknown")[0].parentElement).toHaveTextContent("Unknown1");
    await userEvent.click(medication.getByText("View medication entries"));
    const entries = medication.getAllByRole("listitem");
    expect(entries[0]).toHaveTextContent("Unknown");
    expect(entries[0]).not.toHaveTextContent("Missed");
  });

  it("does not turn unknown symptoms into absence", async () => {
    page();
    const breath = within(await screen.findByRole("region", { name: "Shortness of breath" }));
    expect(breath.getByText("No explicit positive reports in this history window.")).toBeInTheDocument();
    expect(breath.getByText(/Explicitly not reported: 0 · Unknown: 3/)).toBeInTheDocument();
    await userEvent.click(breath.getByText("View reports"));
    expect(breath.getAllByRole("listitem")).toHaveLength(3);
    expect(breath.getAllByRole("listitem").every((item) => item.textContent?.includes("Unknown"))).toBe(true);
    expect(document.body.textContent).not.toMatch(/never had/i);
  });

  it("renders category entries, onset descriptions and temperatures without invented units", async () => {
    page();
    const sleep = within(await screen.findByRole("region", { name: "Sleep" }));
    await userEvent.click(sleep.getByText("View sleep entries"));
    expect(sleep.getAllByRole("listitem")[0]).toHaveTextContent("Unknown");
    const appetite = within(screen.getByRole("region", { name: "Appetite" }));
    await userEvent.click(appetite.getByText("View appetite entries"));
    expect(appetite.getAllByRole("listitem")[1]).toHaveTextContent("Reduced");
    await userEvent.click(screen.getByText("When dizziness started"));
    expect(screen.getByText("This morning when I stood up.")).toBeInTheDocument();
    const temperature = screen.getByRole("region", { name: "Temperature readings" });
    expect(temperature).toHaveTextContent("98.6");
    expect(temperature).not.toHaveTextContent(/°F|°C|fever/i);
  });

  it("shows recent sessions newest first with friendly statuses and no risk labels", async () => {
    page();
    const sessions = within(await screen.findByRole("region", { name: "Recent check-ins" }));
    expect(sessions.getAllByRole("listitem")[0]).toHaveTextContent("Submitted for review");
    expect(sessions.getAllByRole("listitem")[0]).toHaveTextContent("2 answered turns");
    expect(sessions.getAllByRole("button", { name: /View details/ })).toHaveLength(3);
    expect(document.body.textContent).not.toMatch(/GREEN|YELLOW|RED|READY_FOR_REVIEW|riskLevel/);
  });

  it("opens detailed Q&A without exposing notes, extracted facts or provider fields", async () => {
    const writes = vi.spyOn(Storage.prototype, "setItem");
    page();
    const buttons = await screen.findAllByRole("button", { name: /View details/ });
    await userEvent.click(buttons[0]);
    expect(await screen.findByText("I kept waking up.")).toBeInTheDocument();
    expect(screen.getByText("How did you sleep?")).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/monitoring/sessions/103/history"), expect.anything());
    expect(document.body.textContent).not.toMatch(/PRIVATE CLINICAL NOTE|RED|extractedFacts|Gemini|provider/);
    expect(writes).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Back to health history" }));
    expect(await screen.findByRole("region", { name: "Recent check-ins" })).toBeInTheDocument();
  });

  it("offers retry after a network error and recovers", async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error("SECRET stack trace"));
    page();
    expect(await screen.findByRole("alert")).toHaveTextContent(/check your connection/);
    expect(document.body.textContent).not.toContain("SECRET");
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("region", { name: "Recent check-ins" })).toBeInTheDocument();
  });

  it("handles missing patients and partial payloads with a usable error", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({}, 404));
    page();
    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't find your patient history/);
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ patientId: 7, sessionCount: 1, pain: null }));
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/couldn't load your health history/));
  });

  it("does not fabricate a pain score when no pain was recorded", async () => {
    const summary = historySummary();
    summary.pain = emptySummary().pain;
    vi.mocked(fetch).mockResolvedValue(jsonResponse(summary));
    page();
    expect(await screen.findByText("No pain scores recorded in this history window.")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("rejects details for a different patient", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ ...sessionHistory(), patientId: 99 }));
    render(<PatientHistoryPage patientId={7} initialSessionId={103} onCheckIn={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't load this check-in/);
    expect(screen.queryByText("I kept waking up.")).not.toBeInTheDocument();
  });
});
