import { useId } from "react";
import type { Observation, PainSummary, PatientLongitudinalResponse, SymptomSummary } from "../types/longitudinal.ts";
import { categoryLabel, formatDate } from "./historyFormat.ts";

function Entries<T>({ observations, label }: { observations: Observation<T>[]; label: (value: T) => string }) {
  return <ul className="history-entries">{observations.toReversed().map((entry) =>
    <li key={entry.sessionId}><time dateTime={entry.timestamp}>{formatDate(entry.timestamp, true)}</time>
      <strong>{label(entry.value)}</strong></li>)}</ul>;
}

function Counts({ values }: { values: [string, number][] }) {
  const total = values.reduce((sum, [, count]) => sum + count, 0);
  return <dl className="history-counts">{values.map(([label, count]) => <div key={label}>
    <dt>{label}</dt><dd>{count}</dd>
    <span className="count-track" aria-hidden="true"><span style={{ width: `${total ? count / total * 100 : 0}%` }} /></span>
  </div>)}</dl>;
}

function PainChart({ pain }: { pain: PainSummary }) {
  const titleId = useId();
  const observations = pain.observations;
  const firstTime = Date.parse(observations[0].timestamp);
  const lastTime = Date.parse(observations[observations.length - 1].timestamp);
  const x = (entry: Observation<number>) => lastTime === firstTime ? 320 : 44 + (Date.parse(entry.timestamp) - firstTime) / (lastTime - firstTime) * 552;
  const y = (value: number) => 180 - value * 15;
  return <svg className="pain-chart" viewBox="0 0 640 225" role="img" aria-labelledby={titleId}>
    <title id={titleId}>Pain scores over time, on a scale from 0 to 10. Dated values are available below.</title>
    {[0, 5, 10].map((value) => <g key={value}>
      <line x1="44" x2="596" y1={y(value)} y2={y(value)} className="chart-grid" />
      <text x="25" y={y(value) + 5} textAnchor="end">{value}</text>
    </g>)}
    <polyline points={observations.map((entry) => `${x(entry)},${y(entry.value)}`).join(" ")} className="chart-line" />
    {observations.map((entry) => <circle key={entry.sessionId} cx={x(entry)} cy={y(entry.value)} r="5" className="chart-point">
      <title>{formatDate(entry.timestamp, true)}: {entry.value}/10</title>
    </circle>)}
    <text x="44" y="216">{formatDate(observations[0].timestamp)}</text>
    {observations.length > 1 && <text x="596" y="216" textAnchor="end">{formatDate(observations[observations.length - 1].timestamp)}</text>}
  </svg>;
}

export function HistoryTrends({ data }: { data: PatientLongitudinalResponse }) {
  const medicationLabel = (value: boolean | null) => value === null ? "Unknown" : value ? "Taken" : "Missed";
  return <>
    <section className="history-card pain-card" aria-label="Pain over time">
      <p className="eyebrow">Your reported scores</p><h2>Pain over time</h2>
      {data.pain.observations.length === 0 ? <p>No pain scores recorded in this history window.</p> : <>
        <dl className="pain-metrics">
          <div><dt>Latest</dt><dd>{data.pain.latestValue ?? "Unknown"}/10</dd></div>
          <div><dt>First in selected window</dt><dd>{data.pain.firstValue ?? "Unknown"}/10</dd></div>
          <div><dt>Change</dt><dd>{data.pain.change === null ? "Unknown" : `${data.pain.change > 0 ? "+" : ""}${data.pain.change}`}</dd></div>
        </dl>
        <PainChart pain={data.pain} />
        <p className="muted small">Only recorded scores are shown. Change is the difference between the first and latest recorded scores.</p>
        <details><summary>View pain readings ({data.pain.observationCount})</summary>
          <Entries observations={data.pain.observations} label={(value) => `${value}/10`} /></details>
      </>}
    </section>
    <div className="history-grid">
      <section className="history-card" aria-label="Sleep"><h2>Sleep</h2>
        <Counts values={[["Good", data.sleep.goodCount], ["Normal", data.sleep.normalCount], ["Poor", data.sleep.poorCount], ["Unknown", data.sleep.unknownCount]]} />
        <details><summary>View sleep entries</summary><Entries observations={data.sleep.observations} label={categoryLabel} /></details>
      </section>
      <section className="history-card" aria-label="Appetite"><h2>Appetite</h2>
        <Counts values={[["Good", data.appetite.goodCount], ["Normal", data.appetite.normalCount], ["Reduced", data.appetite.reducedCount], ["Poor", data.appetite.poorCount], ["Unknown", data.appetite.unknownCount]]} />
        <details><summary>View appetite entries</summary><Entries observations={data.appetite.observations} label={categoryLabel} /></details>
      </section>
      <section className="history-card" aria-label="Medication"><h2>Medication</h2>
        <Counts values={[["Taken", data.medication.takenCount], ["Missed", data.medication.missedCount], ["Unknown", data.medication.unknownCount]]} />
        <p className="muted small">Unknown means no answer was recorded.</p>
        <details><summary>View medication entries</summary><Entries observations={data.medication.observations} label={medicationLabel} /></details>
      </section>
    </div>
    <section className="history-card" aria-label="Symptom reports"><h2>Symptom reports</h2>
      <div className="symptom-grid">
        <Symptom title="Dizziness" data={data.symptoms.dizziness} />
        <Symptom title="Shortness of breath" data={data.symptoms.shortnessOfBreath} />
        <Symptom title="Loss of consciousness" data={data.symptoms.lossOfConsciousness} />
      </div>
      {data.dizzinessOnset.length > 0 && <details><summary>When dizziness started</summary>
        <ul className="history-entries">{data.dizzinessOnset.toReversed().map((entry) => <li key={entry.sessionId}>
          <time dateTime={entry.timestamp}>{formatDate(entry.timestamp, true)}</time><span className="preserve-text">{entry.onset}</span>
        </li>)}</ul></details>}
    </section>
    {data.temperature.length > 0 && <section className="history-card" aria-label="Temperature readings">
      <h2>Temperature readings</h2><p className="muted small">Shown as recorded. Units were not recorded with these readings.</p>
      <Entries observations={data.temperature} label={String} />
    </section>}
  </>;
}

function Symptom({ title, data }: { title: string; data: SymptomSummary }) {
  return <section className="symptom" aria-label={title}><h3>{title}</h3>
    <p>{data.reportedTrueCount > 0 ? `Reported on ${data.reportedTrueCount} check-in${data.reportedTrueCount === 1 ? "" : "s"}`
      : "No explicit positive reports in this history window."}</p>
    {data.firstReportedAt && <p className="small">First reported: {formatDate(data.firstReportedAt)}</p>}
    {data.lastReportedAt && <p className="small">Most recently: {formatDate(data.lastReportedAt)}</p>}
    <p className="muted small">Explicitly not reported: {data.reportedFalseCount} · Unknown: {data.unknownCount}</p>
    <details><summary>View reports</summary><Entries observations={data.observations}
      label={(value) => value === null ? "Unknown" : value ? "Reported" : "Explicitly not reported"} /></details>
  </section>;
}
