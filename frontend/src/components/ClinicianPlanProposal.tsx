import { useEffect, useState, type FormEvent } from "react";
import {
  addProposalQuestion,
  approveProposal,
  fetchPlanAskableFields,
  fetchProposal,
  fetchProposals,
  generateProposal,
  rejectProposal,
  reorderProposalQuestions,
  updateProposalQuestion,
  type CatalogChoice,
  type PlanProposal,
  type ProposalQuestion,
} from "../api/proposalApi.ts";

export function ClinicianPlanProposal({ patientId, onChanged }: {
  patientId: number;
  onChanged: () => void;
}) {
  const [proposal, setProposal] = useState<PlanProposal | null>(null);
  const [fields, setFields] = useState<CatalogChoice[]>([]);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [wording, setWording] = useState("");
  const [required, setRequired] = useState(true);
  const [adding, setAdding] = useState(false);
  const [fieldCode, setFieldCode] = useState("");
  const [newWording, setNewWording] = useState("");

  useEffect(() => {
    let cancelled = false;
    setReady(false);
    setProposal(null);
    setError(null);
    setEditingId(null);
    setAdding(false);
    void load(patientId).then((draft) => {
      if (!cancelled) {
        setProposal(draft);
        setReady(true);
      }
    }).catch(() => {
      if (!cancelled) setReady(true);
    });
    return () => { cancelled = true; };
  }, [patientId]);

  async function refresh(next?: PlanProposal | null) {
    const draft = next === undefined ? await load(patientId) : next;
    setProposal(draft);
    onChanged();
  }

  async function generate() {
    setBusy(true);
    setError(null);
    try {
      await refresh(await generateProposal(patientId));
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to create a suggested plan.");
    } finally {
      setBusy(false);
    }
  }

  async function saveQuestion(event: FormEvent, question: ProposalQuestion) {
    event.preventDefault();
    if (proposal === null || busy) return;
    setBusy(true);
    setError(null);
    try {
      await refresh(await updateProposalQuestion(proposal.id, question.id, wording, required, question.enabled));
      setEditingId(null);
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to update that question.");
    } finally {
      setBusy(false);
    }
  }

  async function toggle(question: ProposalQuestion) {
    if (proposal === null) return;
    setBusy(true);
    setError(null);
    try {
      await refresh(await updateProposalQuestion(
        proposal.id, question.id, question.questionText, question.required, !question.enabled));
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to update that question.");
    } finally {
      setBusy(false);
    }
  }

  async function move(question: ProposalQuestion, direction: -1 | 1) {
    if (proposal === null) return;
    const ordered = [...proposal.questions].sort((left, right) => left.displayOrder - right.displayOrder);
    const index = ordered.findIndex((item) => item.id === question.id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= ordered.length) return;
    const swapped = [...ordered];
    const current = swapped[index];
    swapped[index] = swapped[target];
    swapped[target] = current;
    setBusy(true);
    setError(null);
    try {
      await refresh(await reorderProposalQuestions(proposal.id, swapped.map((item) => item.id)));
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to reorder those questions.");
    } finally {
      setBusy(false);
    }
  }

  async function addQuestion(event: FormEvent) {
    event.preventDefault();
    if (proposal === null || busy) return;
    setBusy(true);
    setError(null);
    try {
      await refresh(await addProposalQuestion(proposal.id, fieldCode, newWording, true));
      setAdding(false);
      setFieldCode("");
      setNewWording("");
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to add that question.");
    } finally {
      setBusy(false);
    }
  }

  async function approve() {
    if (proposal === null) return;
    setBusy(true);
    setError(null);
    try {
      await approveProposal(proposal.id);
      await refresh(null);
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to approve that suggestion.");
    } finally {
      setBusy(false);
    }
  }

  async function reject() {
    if (proposal === null) return;
    setBusy(true);
    setError(null);
    try {
      await rejectProposal(proposal.id);
      await refresh(null);
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to reject that suggestion.");
    } finally {
      setBusy(false);
    }
  }

  async function openAdd() {
    setError(null);
    try {
      const choices = await fetchPlanAskableFields();
      setFields(choices);
      const used = new Set((proposal?.questions ?? []).map((question) => question.fieldCode));
      const first = choices.find((field) => !used.has(field.code));
      setFieldCode(first?.code ?? "");
      setNewWording("");
      setAdding(true);
    } catch (caught: unknown) {
      setError(caught instanceof Error ? caught.message : "Unable to load monitoring fields.");
    }
  }

  const draft = proposal?.status === "DRAFT" ? proposal : null;
  const used = new Set((draft?.questions ?? []).map((question) => question.fieldCode));
  const available = fields.filter((field) => !used.has(field.code));

  return <section className="history-card" aria-label="Personalized Plan">
    <h2>Personalized Plan</h2>
    {error ? <p role="alert">{error}</p> : null}
    {!ready ? <p role="status">Loading suggested plan...</p> : null}
    {ready && draft === null ? <>
      <button type="button" onClick={() => void generate()} disabled={busy}>Generate Suggested Plan</button>
      {busy ? <p role="status">Generating suggested plan...</p> : null}
    </> : null}
    {draft ? <>
      <h3>AI-Suggested Monitoring Plan</h3>
      <p>Based on:</p>
      <ul>{draft.conditions.map((condition) => <li key={`${condition.conditionName}-${condition.monitoringCategory}`}>
        {condition.conditionName}{condition.primaryCondition ? " (primary)" : ""}
      </li>)}</ul>
      {draft.conditionFamilies.length > 0 ? <p className="muted small">Descriptive labels, not a diagnosis: {draft.conditionFamilies.join(", ")}</p> : null}
      {draft.warnings.length > 0 ? <ul>{draft.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul> : null}
      <ol className="condition-list">{[...draft.questions].sort((left, right) => left.displayOrder - right.displayOrder).map((question, index, ordered) => <li key={question.id}>
        <p><strong>{question.displayName}</strong>{question.enabled ? "" : " (disabled)"}</p>
        <p>{question.questionText}</p>
        <p className="muted small">{question.required ? "Required" : "Optional"}</p>
        {editingId === question.id ? <form className="condition-form" onSubmit={(event) => void saveQuestion(event, question)}>
          <label>
            Question wording
            <textarea value={wording} onChange={(event) => setWording(event.target.value)} required maxLength={500} />
          </label>
          <label>
            <input type="checkbox" checked={required} onChange={(event) => setRequired(event.target.checked)} />
            Required
          </label>
          <button type="submit" disabled={busy}>Save wording</button>
        </form> : <div className="button-row">
          <button type="button" className="secondary" onClick={() => { setEditingId(question.id); setWording(question.questionText); setRequired(question.required); }}>
            Edit {question.displayName}
          </button>
          <button type="button" className="secondary" onClick={() => void toggle(question)} disabled={busy}>
            {question.enabled ? `Disable ${question.displayName}` : `Enable ${question.displayName}`}
          </button>
          <button type="button" className="secondary" onClick={() => void move(question, -1)} disabled={busy || index === 0}>Move up</button>
          <button type="button" className="secondary" onClick={() => void move(question, 1)} disabled={busy || index === ordered.length - 1}>Move down</button>
        </div>}
      </li>)}</ol>
      {adding ? <form className="condition-form" onSubmit={(event) => void addQuestion(event)}>
        <label>
          Field
          <select value={fieldCode} onChange={(event) => setFieldCode(event.target.value)} required>
            {available.map((field) => <option key={field.code} value={field.code}>{field.displayName}</option>)}
          </select>
        </label>
        <label>
          Question
          <textarea value={newWording} onChange={(event) => setNewWording(event.target.value)} required maxLength={500} />
        </label>
        <button type="submit" disabled={busy || available.length === 0}>Add this question</button>
      </form> : <button type="button" className="secondary" onClick={() => void openAdd()}>+ Add Question</button>}
      <div className="button-row">
        <button type="button" className="secondary" onClick={() => void generate()} disabled={busy}>Regenerate</button>
        <button type="button" className="secondary" onClick={() => void reject()} disabled={busy}>Reject</button>
        <button type="button" onClick={() => void approve()} disabled={busy}>Approve &amp; Assign</button>
      </div>
      <p className="muted small">Approval applies to future check-ins. An active check-in keeps its current questions.</p>
    </> : null}
  </section>;
}

async function load(patientId: number): Promise<PlanProposal | null> {
  const summaries = await fetchProposals(patientId);
  const draft = summaries.find((summary) => summary.status === "DRAFT");
  return draft ? fetchProposal(draft.id) : null;
}
