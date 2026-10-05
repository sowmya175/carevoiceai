import type { ConversationTurn } from "../types/monitoring.ts";

interface TranscriptCardProps {
  turns: ConversationTurn[];
}

export function TranscriptCard({ turns }: TranscriptCardProps) {
  if (turns.length === 0) {
    return null;
  }
  return (
    <section className="transcript-card" aria-label="Conversation">
      <ol>
        {turns.map((turn) => (
          <li key={turn.id} className={turn.speaker}>
            <span>{turn.speaker === "carevoice" ? "CareVoice" : "You"}</span>
            {turn.heard ? <p><span className="heard">We heard:</span> {turn.text}</p> : <p>{turn.text}</p>}
          </li>
        ))}
      </ol>
    </section>
  );
}
