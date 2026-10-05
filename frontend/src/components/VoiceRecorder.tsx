import type { VoiceRecorderController } from "../hooks/useVoiceRecorder.ts";

interface VoiceRecorderProps {
  recorder: VoiceRecorderController;
  onSubmit: (audio: Blob) => void;
}

export function VoiceRecorder({ recorder, onSubmit }: VoiceRecorderProps) {
  const uploading = recorder.state === "uploading";
  return (
    <section className="recorder" aria-label="Record your answer">
      {recorder.errorMessage ? <p className="error" role="alert">{recorder.errorMessage}</p> : null}
      <div className="recorder-actions">
        {recorder.state === "idle" || recorder.state === "error" ? (
          <button type="button" className="primary" onClick={() => void recorder.startRecording()} disabled={!recorder.supported || uploading}>
            Record Answer
          </button>
        ) : null}
        {recorder.state === "recording" ? (
          <button type="button" className="primary" onClick={recorder.stopRecording}>
            Stop
          </button>
        ) : null}
        {recorder.state === "recorded" || uploading ? (
          <>
            <button
              type="button"
              className="primary"
              disabled={uploading || !recorder.blob}
              onClick={() => {
                if (recorder.blob) {
                  onSubmit(recorder.blob);
                }
              }}
            >
              Submit Answer
            </button>
            <button type="button" className="secondary" onClick={recorder.clearRecording} disabled={uploading}>
              Record Again
            </button>
          </>
        ) : null}
      </div>
    </section>
  );
}
