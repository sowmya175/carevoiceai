import { useCallback, useEffect, useRef, useState } from "react";
import type { QuestionSpeech } from "../hooks/useQuestionSpeech.ts";
import { alreadyAutoRead, markAutoRead } from "../speech/questionSpeech.ts";

export function QuestionSpeechControls({
  question,
  active,
  recording,
  autoRead,
  onAutoReadChange,
  speech,
  onPrepareRecord,
}: {
  question: string;
  active: boolean;
  recording: boolean;
  autoRead: boolean;
  onAutoReadChange: (enabled: boolean) => void;
  speech: QuestionSpeech;
  onPrepareRecord: (prepare: () => void) => void;
}) {
  const { supported, speaking, notice, speak, stop } = speech;
  const pending = useRef<number | null>(null);
  const recordingRef = useRef(recording);
  const shown = useRef<string | null>(null);
  const [replay, setReplay] = useState(false);
  const questionKey = useRef(question);
  if (questionKey.current !== question) {
    questionKey.current = question;
    if (replay) setReplay(false);
  }
  recordingRef.current = recording;

  const prepareToRecord = useCallback(() => {
    recordingRef.current = true;
    if (pending.current !== null) {
      window.clearTimeout(pending.current);
      pending.current = null;
    }
    stop();
  }, [stop]);

  useEffect(() => {
    onPrepareRecord(prepareToRecord);
  }, [onPrepareRecord, prepareToRecord]);

  useEffect(() => {
    const changed = shown.current !== null && shown.current !== question;
    shown.current = question;
    if (!active || recording) {
      stop();
      return;
    }
    if (changed) stop();
    if (!autoRead || !supported || alreadyAutoRead(question)) return;
    pending.current = window.setTimeout(() => {
      pending.current = null;
      if (recordingRef.current) return;
      if (alreadyAutoRead(question)) return;
      markAutoRead(question);
      setReplay(true);
      speak(question);
    }, 0);
    return () => {
      if (pending.current !== null) {
        window.clearTimeout(pending.current);
        pending.current = null;
      }
    };
  }, [question, active, recording, autoRead, supported, speak, stop]);

  const label = speaking ? "Stop" : replay ? "Read again" : "Read aloud";
  return (
    <div className="speech-controls">
      <button
        type="button"
        className="secondary"
        aria-label={speaking ? "Stop reading the question" : "Read CareVoice question aloud"}
        disabled={!supported || recording}
        onClick={() => {
          if (!supported || recording) return;
          if (speaking) {
            stop();
            return;
          }
          setReplay(true);
          speak(question);
        }}
      >
        {label}
      </button>
      <label className="speech-toggle">
        <input
          type="checkbox"
          checked={autoRead}
          disabled={!supported}
          onChange={(event) => onAutoReadChange(event.target.checked)}
        />
        Auto-read questions
      </label>
      {!supported ? <p className="speech-note">Read aloud is not supported by this browser.</p> : null}
      {notice ? <p className="speech-note" role="status">{notice}</p> : null}
    </div>
  );
}
