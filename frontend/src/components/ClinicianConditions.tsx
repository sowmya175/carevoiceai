import { useEffect, useState, type FormEvent } from "react";
import {
  categoryLabel,
  CONDITION_CATEGORIES,
  createClinicianCondition,
  fetchClinicianConditions,
  updateClinicianCondition,
  type ClinicianCondition,
  type MonitoringCategory,
} from "../api/conditionApi.ts";

export function ClinicianConditions({ patientId, onChanged }: {
  patientId: number;
  onChanged: () => void;
}) {
  const [conditions, setConditions] = useState<ClinicianCondition[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setConditions(null);
    setError(null);
    setDraft(null);
    void fetchClinicianConditions(patientId).then((rows) => {
      if (!cancelled) setConditions(rows);
    }).catch((caught: unknown) => {
      if (!cancelled) setError(caught instanceof Error ? caught.message : "Unable to load conditions.");
    });
    return () => { cancelled = true; };
  }, [patientId]);

  async function reload() {
    const rows = await fetchClinicianConditions(patientId);
    setConditions(rows);
    onChanged();
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (draft === null || saving) return;
    setSaving(true);
    setError(null);
    try {
      if (draft.id === null) {
        await createClinicianCondition(patientId, draft.conditionName, draft.category, draft.primary);
      } else {
        await updateClinicianCondition(patientId, draft.id, draft.conditionName, draft.category, true, draft.primary);
      }
      setDraft(null);
      await reload();
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to save that condition.");
    } finally {
      setSaving(false);
    }
  }

  async function makePrimary(condition: ClinicianCondition) {
    setError(null);
    try {
      await updateClinicianCondition(
        patientId, condition.id, condition.conditionName, condition.monitoringCategory, true, true);
      await reload();
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to save that condition.");
    }
  }

  async function deactivate(condition: ClinicianCondition) {
    setError(null);
    try {
      await updateClinicianCondition(
        patientId, condition.id, condition.conditionName, condition.monitoringCategory, false, false);
      await reload();
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to save that condition.");
    }
  }

  const active = (conditions ?? []).filter((condition) => condition.active);
  const primary = active.find((condition) => condition.primaryCondition) ?? null;
  const additional = active.filter((condition) => !condition.primaryCondition);

  return <section className="history-card" aria-label="Patient conditions">
    <h2>Patient Conditions</h2>
    <p className="muted">Conditions describe the patient's current health context. The monitoring plan controls the questions CareVoice currently asks.</p>
    {error ? <p role="alert">{error}</p> : null}
    {conditions === null && !error ? <p role="status">Loading conditions...</p> : null}
    {primary ? <div>
      <h3>Primary</h3>
      <ConditionCard condition={primary} onEdit={() => setDraft(draftFrom(primary))} />
    </div> : null}
    {additional.length > 0 ? <div>
      <h3>Additional Conditions</h3>
      <ul className="condition-list">
        {additional.map((condition) => <li key={condition.id}>
          <ConditionCard
            condition={condition}
            onEdit={() => setDraft(draftFrom(condition))}
            onPrimary={() => void makePrimary(condition)}
            onDeactivate={() => void deactivate(condition)}
          />
        </li>)}
      </ul>
    </div> : null}
    {draft ? <form className="condition-form" onSubmit={(event) => void save(event)}>
      <label htmlFor="condition-name">Condition / Procedure</label>
      <input id="condition-name" value={draft.conditionName} maxLength={255} required
        onChange={(event) => setDraft({ ...draft, conditionName: event.target.value })} />
      <label htmlFor="condition-category">Category</label>
      <select id="condition-category" value={draft.category}
        onChange={(event) => setDraft({ ...draft, category: event.target.value as MonitoringCategory })}>
        {CONDITION_CATEGORIES.map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}
      </select>
      <label>
        <input type="checkbox" checked={draft.primary} onChange={(event) => setDraft({ ...draft, primary: event.target.checked })} />
        Primary condition
      </label>
      <div className="button-row">
        <button type="submit" className="primary" disabled={saving}>Save</button>
        <button type="button" className="secondary" onClick={() => setDraft(null)}>Cancel</button>
      </div>
    </form> : <button type="button" onClick={() => setDraft({
      id: null, conditionName: "", category: "OTHER", primary: false,
    })}>+ Add Condition</button>}
  </section>;
}

function ConditionCard({ condition, onEdit, onPrimary, onDeactivate }: {
  condition: ClinicianCondition;
  onEdit: () => void;
  onPrimary?: () => void;
  onDeactivate?: () => void;
}) {
  return <article aria-label={condition.conditionName}>
    <p>{condition.conditionName}</p>
    <p className="muted">{categoryLabel(condition.monitoringCategory)}</p>
    <div className="button-row">
      <button type="button" className="secondary" onClick={onEdit}>Edit</button>
      {onPrimary ? <button type="button" className="secondary" onClick={onPrimary}>Set as Primary</button> : null}
      {onDeactivate ? <button type="button" className="secondary" onClick={onDeactivate}>Deactivate</button> : null}
    </div>
  </article>;
}

interface Draft {
  id: number | null;
  conditionName: string;
  category: MonitoringCategory;
  primary: boolean;
}

function draftFrom(condition: ClinicianCondition): Draft {
  return {
    id: condition.id,
    conditionName: condition.conditionName,
    category: condition.monitoringCategory,
    primary: condition.primaryCondition,
  };
}
