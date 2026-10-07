import type { SessionStatus } from "../types/monitoring.ts";

interface CheckInCompleteProps {
  status: SessionStatus;
  onViewHistory?: () => void;
  onViewResponses?: () => void;
}

export function CheckInComplete({ status, onViewHistory, onViewResponses }: CheckInCompleteProps) {
  const detail = status === "READY_FOR_REVIEW"
    ? "Your check-in has been recorded for review by your care team."
    : "Your daily check-in has been completed.";
  return (
    <section className="complete-card" aria-live="polite">
      <h2>Today's check-in is complete.</h2>
      <p>{detail}</p>
      <p>Your responses have been saved.</p>
      <div className="button-row">
        {onViewResponses && <button type="button" className="primary" onClick={onViewResponses}>View today's responses</button>}
        {onViewHistory && <button type="button" className="secondary" onClick={onViewHistory}>View health history</button>}
      </div>
    </section>
  );
}
