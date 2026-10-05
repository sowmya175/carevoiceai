import type { SessionStatus } from "../types/monitoring.ts";

interface CheckInCompleteProps {
  status: SessionStatus;
}

export function CheckInComplete({ status }: CheckInCompleteProps) {
  const detail = status === "READY_FOR_REVIEW"
    ? "Your check-in has been recorded for review by your care team."
    : "Your daily check-in has been completed.";
  return (
    <section className="complete-card" aria-live="polite">
      <h2>Today's check-in is complete.</h2>
      <p>{detail}</p>
    </section>
  );
}
