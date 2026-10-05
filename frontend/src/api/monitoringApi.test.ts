import { describe, expect, it } from "vitest";
import { messageForFailedResponse } from "./monitoringApi.ts";

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
