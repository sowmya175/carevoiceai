import { apiFetch, MonitoringApiError } from "./monitoringApi.ts";

export interface ProposalQuestion {
  id: number;
  fieldCode: string;
  displayName: string;
  questionText: string;
  displayOrder: number;
  required: boolean;
  enabled: boolean;
  rationale: string | null;
}

export interface ProposalCondition {
  sourcePatientConditionId: number | null;
  conditionName: string;
  monitoringCategory: string;
  primaryCondition: boolean;
}

export interface PlanProposal {
  id: number;
  patientId: number;
  status: "DRAFT" | "APPROVED" | "REJECTED" | "SUPERSEDED";
  createdAt: string;
  approvedAt: string | null;
  rejectedAt: string | null;
  approvedMonitoringPlanId: number | null;
  approvedMonitoringPlanName: string | null;
  conditions: ProposalCondition[];
  conditionFamilies: string[];
  warnings: string[];
  questions: ProposalQuestion[];
}

export interface ProposalSummary {
  id: number;
  patientId: number;
  status: string;
  createdAt: string;
}

export interface CatalogChoice {
  code: string;
  displayName: string;
  planAskable: boolean;
}

export async function fetchProposals(patientId: number): Promise<ProposalSummary[]> {
  const body = await read(`/api/clinician/patients/${patientId}/monitoring-plan-proposals`, "Unable to load suggested plans.");
  if (!Array.isArray(body)) throw new MonitoringApiError(0, "Unable to load suggested plans.");
  return body as ProposalSummary[];
}

export async function fetchProposal(proposalId: number): Promise<PlanProposal> {
  const body = await read(`/api/clinician/monitoring-plan-proposals/${proposalId}`, "Unable to load suggested plans.");
  if (!isProposal(body)) throw new MonitoringApiError(0, "Unable to load suggested plans.");
  return body;
}

export async function generateProposal(patientId: number): Promise<PlanProposal> {
  return send(`/api/clinician/patients/${patientId}/monitoring-plan-proposals`, "POST", undefined, "Unable to create a suggested plan.");
}

export async function updateProposalQuestion(
  proposalId: number,
  questionId: number,
  questionText: string,
  required: boolean,
  enabled: boolean,
): Promise<PlanProposal> {
  return send(
    `/api/clinician/monitoring-plan-proposals/${proposalId}/questions/${questionId}`,
    "PUT",
    { questionText, required, enabled },
    "Unable to update that question.",
  );
}

export async function addProposalQuestion(
  proposalId: number,
  fieldCode: string,
  questionText: string,
  required: boolean,
): Promise<PlanProposal> {
  return send(
    `/api/clinician/monitoring-plan-proposals/${proposalId}/questions`,
    "POST",
    { fieldCode, questionText, required },
    "Unable to add that question.",
  );
}

export async function reorderProposalQuestions(proposalId: number, questionIds: number[]): Promise<PlanProposal> {
  return send(
    `/api/clinician/monitoring-plan-proposals/${proposalId}/question-order`,
    "PUT",
    { questionIds },
    "Unable to reorder those questions.",
  );
}

export async function approveProposal(proposalId: number): Promise<PlanProposal> {
  return send(`/api/clinician/monitoring-plan-proposals/${proposalId}/approve`, "POST", {}, "Unable to approve that suggestion.");
}

export async function rejectProposal(proposalId: number): Promise<PlanProposal> {
  return send(`/api/clinician/monitoring-plan-proposals/${proposalId}/reject`, "POST", {}, "Unable to reject that suggestion.");
}

export async function fetchPlanAskableFields(): Promise<CatalogChoice[]> {
  const body = await read("/api/clinician/monitoring-fields?planAskable=true", "Unable to load monitoring fields.");
  if (!Array.isArray(body)) return [];
  return body.filter(isCatalogChoice);
}

async function send(path: string, method: "POST" | "PUT", payload: unknown, fallback: string): Promise<PlanProposal> {
  const response = await apiFetch(path, {
    method,
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !isProposal(body)) {
    throw new MonitoringApiError(response.status, message(body, fallback));
  }
  return body;
}

async function read(path: string, fallback: string): Promise<unknown> {
  const response = await apiFetch(path, { headers: { Accept: "application/json" }, cache: "no-store" });
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new MonitoringApiError(response.status, message(body, fallback));
  return body;
}

function message(body: unknown, fallback: string): string {
  if (typeof body === "object" && body !== null && "error" in body && typeof body.error === "string" && body.error.trim().length > 0) {
    return body.error;
  }
  return fallback;
}

function isProposal(value: unknown): value is PlanProposal {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.id === "number" && typeof record.status === "string" && Array.isArray(record.questions);
}

function isCatalogChoice(value: unknown): value is CatalogChoice {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.code === "string" && typeof record.displayName === "string" && record.planAskable === true;
}
