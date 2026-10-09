import type { VoiceRecorderController } from "../hooks/useVoiceRecorder.ts";

interface VoiceRecorderProps {
  recorder: VoiceRecorderController;
  beforeStart?: () => void;
}

export function VoiceRecorder({ recorder, beforeStart }: VoiceRecorderProps) {
  const uploading = recorder.state === "uploading";
  return (
    <section className="recorder" aria-label="Record your answer">
      {recorder.errorMessage ? <p className="error" role="alert">{recorder.errorMessage}</p> : null}
      <div className="recorder-actions">
        {recorder.state === "idle" || recorder.state === "error" ? (
          <button type="button" className="primary" onClick={() => { beforeStart?.(); void recorder.startRecording(); }} disabled={!recorder.supported || uploading}>
            Record Answer
          </button>
        ) : null}
        {recorder.state === "recording" ? (
          <button type="button" className="primary" onClick={recorder.stopRecording}>
            Stop
          </button>
        ) : null}
        {recorder.state === "recorded" ? (
          <button type="button" className="secondary" onClick={recorder.clearRecording} disabled={uploading}>
            Record again
          </button>
        ) : null}
      </div>
    </section>
  );
}
