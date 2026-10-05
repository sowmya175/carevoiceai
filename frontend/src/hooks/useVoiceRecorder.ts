import { useEffect, useRef, useState } from "react";
import { selectRecordingMimeType } from "./recordingMime.ts";

export type RecorderState = "idle" | "recording" | "recorded" | "uploading" | "error";

const UNSUPPORTED = "Voice recording is not supported in this browser. Please use text input.";
const PERMISSION_DENIED = "Microphone access is needed to record your response. You can use text input instead.";

export interface VoiceRecorderController {
  state: RecorderState;
  elapsedSeconds: number;
  blob: Blob | null;
  errorMessage: string | null;
  supported: boolean;
  startRecording: () => Promise<void>;
  stopRecording: () => void;
  clearRecording: () => void;
  beginUpload: () => void;
  endUpload: (clearBlob: boolean) => void;
}

export function useVoiceRecorder(): VoiceRecorderController {
  const [state, setState] = useState<RecorderState>("idle");
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | null>(null);
  const supported = canRecord();

  useEffect(() => {
    return () => {
      releaseStream();
      clearTimer();
    };
  }, []);

  function releaseStream() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  function clearTimer() {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }

  async function startRecording() {
    setErrorMessage(null);
    setBlob(null);
    if (!canRecord()) {
      setState("error");
      setErrorMessage(UNSUPPORTED);
      return;
    }
    const mimeType = selectRecordingMimeType((candidate) => MediaRecorder.isTypeSupported(candidate));
    if (!mimeType) {
      setState("error");
      setErrorMessage(UNSUPPORTED);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      const recorder = new MediaRecorder(stream, { mimeType });
      chunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);
        }
      };
      recorder.onstop = () => {
        const recordedType = recorder.mimeType || mimeType;
        setBlob(new Blob(chunksRef.current, { type: recordedType }));
        setState("recorded");
        releaseStream();
        clearTimer();
      };
      recorder.onerror = () => {
        setState("error");
        setErrorMessage(UNSUPPORTED);
        releaseStream();
        clearTimer();
      };
      recorderRef.current = recorder;
      recorder.start();
      const startedAt = Date.now();
      setElapsedSeconds(0);
      setState("recording");
      timerRef.current = window.setInterval(() => {
        setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000));
      }, 250);
    } catch (error) {
      releaseStream();
      clearTimer();
      setState("error");
      const name = error instanceof DOMException ? error.name : "";
      setErrorMessage(name === "NotAllowedError" || name === "PermissionDeniedError" ? PERMISSION_DENIED : UNSUPPORTED);
    }
  }

  function stopRecording() {
    const recorder = recorderRef.current;
    if (recorder && recorder.state === "recording") {
      recorder.stop();
    }
  }

  function clearRecording() {
    setBlob(null);
    setElapsedSeconds(0);
    setErrorMessage(null);
    setState("idle");
  }

  function beginUpload() {
    setErrorMessage(null);
    setState("uploading");
  }

  function endUpload(clearBlob: boolean) {
    if (clearBlob) {
      clearRecording();
      return;
    }
    setState("recorded");
  }

  return {
    state,
    elapsedSeconds,
    blob,
    errorMessage,
    supported,
    startRecording,
    stopRecording,
    clearRecording,
    beginUpload,
    endUpload,
  };
}

function canRecord(): boolean {
  return typeof navigator !== "undefined"
    && typeof navigator.mediaDevices?.getUserMedia === "function"
    && typeof MediaRecorder !== "undefined";
}
