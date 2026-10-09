import type { ReactNode } from "react";

interface CurrentQuestionProps {
  question: string | null;
  children?: ReactNode;
}

export function CurrentQuestion({ question, children }: CurrentQuestionProps) {
  if (!question) {
    return null;
  }
  return (
    <section className="question-card" aria-labelledby="current-question-label">
      <p id="current-question-label" className="eyebrow">Current question</p>
      <h2>{question}</h2>
      {children}
    </section>
  );
}
