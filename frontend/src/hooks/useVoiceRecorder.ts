import { useEffect, useRef, useState } from "react";
import { selectRecordingMimeType } from "./recordingMime.ts";

export type RecorderState = "idle" | "recording" | "recorded" | "uploading" | "error";

const UNSUPPORTED = "Voice recording is not supported in this browser. Please use text input.";
const PERMISSION_DENIED = "Microphone access is needed to record your response. You can use text input instead.";
const TOO_SHORT = "That recording was too short. Please try again.";
const MIN_RECORDING_MS = 400;

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
  const stopTimerRef = useRef<number | null>(null);
  const startedAtRef = useRef(0);
  const generationRef = useRef(0);
  const supported = canRecord();

  useEffect(() => {
    return () => {
      generationRef.current += 1;
      releaseStream();
      clearTimer();
      clearStopTimer();
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

  function clearStopTimer() {
    if (stopTimerRef.current !== null) {
      window.clearTimeout(stopTimerRef.current);
      stopTimerRef.current = null;
    }
  }

  async function startRecording() {
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    setErrorMessage(null);
    setBlob(null);
    clearStopTimer();
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
      if (generationRef.current !== generation) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      streamRef.current = stream;
      const recorder = new MediaRecorder(stream, { mimeType });
      chunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (generationRef.current === generation && event.data.size > 0) {
          chunksRef.current.push(event.data);
        }
      };
      recorder.onstart = () => {
        if (generationRef.current !== generation) {
          return;
        }
        startedAtRef.current = Date.now();
        setElapsedSeconds(0);
        setState("recording");
        timerRef.current = window.setInterval(() => {
          setElapsedSeconds(Math.floor((Date.now() - startedAtRef.current) / 1000));
        }, 250);
      };
      recorder.onstop = () => {
        if (generationRef.current !== generation) {
          return;
        }
        const recordedType = recorder.mimeType || mimeType;
        const recorded = new Blob(chunksRef.current, { type: recordedType });
        releaseStream();
        clearTimer();
        if (recorded.size === 0) {
          setBlob(null);
          setState("error");
          setErrorMessage(TOO_SHORT);
          return;
        }
        setBlob(recorded);
        setState("recorded");
      };
      recorder.onerror = () => {
        if (generationRef.current !== generation) {
          return;
        }
        setState("error");
        setErrorMessage(UNSUPPORTED);
        releaseStream();
        clearTimer();
      };
      recorderRef.current = recorder;
      recorder.start(250);
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
    if (!recorder || recorder.state !== "recording" || stopTimerRef.current !== null) {
      return;
    }
    const elapsed = Date.now() - startedAtRef.current;
    const finish = () => {
      stopTimerRef.current = null;
      if (recorder.state === "recording") {
        recorder.stop();
      }
    };
    if (elapsed >= MIN_RECORDING_MS) {
      finish();
      return;
    }
    stopTimerRef.current = window.setTimeout(finish, MIN_RECORDING_MS - elapsed);
  }

  function clearRecording() {
    generationRef.current += 1;
    clearStopTimer();
    const recorder = recorderRef.current;
    if (recorder && recorder.state === "recording") {
      recorder.onstop = null;
      recorder.stop();
    }
    releaseStream();
    clearTimer();
    recorderRef.current = null;
    chunksRef.current = [];
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
