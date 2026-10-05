import { describe, expect, it } from "vitest";
import { selectRecordingMimeType } from "./recordingMime.ts";

describe("selectRecordingMimeType", () => {
  it("prefers opus webm when the browser supports it", () => {
    expect(selectRecordingMimeType((mime) => mime === "audio/webm;codecs=opus")).toBe("audio/webm;codecs=opus");
  });

  it("falls back to the first supported type", () => {
    expect(selectRecordingMimeType((mime) => mime === "audio/mp4")).toBe("audio/mp4");
  });

  it("returns null when the browser cannot record", () => {
    expect(selectRecordingMimeType(() => false)).toBeNull();
  });
});
