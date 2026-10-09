import { useEffect, useState } from "react";
import { fetchMyConditions, type PatientConditionSummary } from "../api/conditionApi.ts";

export function PatientHealthConditions() {
  const [conditions, setConditions] = useState<PatientConditionSummary[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    void fetchMyConditions().then((rows) => {
      if (!cancelled) setConditions(rows);
    }).catch(() => {
      if (!cancelled) setConditions(null);
    });
    return () => { cancelled = true; };
  }, []);

  if (conditions === null || conditions.length === 0) return null;
  return <section className="history-card" aria-label="Health conditions">
    <h2>Health Conditions</h2>
    <ul className="condition-list">
      {conditions.map((condition) => <li key={`${condition.conditionName}-${condition.monitoringCategory}`}>
        {condition.conditionName}
      </li>)}
    </ul>
  </section>;
}
