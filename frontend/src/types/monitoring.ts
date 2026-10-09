export type SessionStatus = "IN_PROGRESS" | "READY_FOR_REVIEW" | "COMPLETED";

export type DailyCheckInStatus = "NOT_STARTED" | SessionStatus;

export type RiskLevel = "GREEN" | "YELLOW" | "RED";

export interface SessionStart {
  sessionId: number;
  status: SessionStatus;
  nextQuestion: string | null;
}

export interface DailyCheckIn {
  checkInDate: string;
  currentDate: string;
  timezone: string;
  status: DailyCheckInStatus;
  sessionId: number | null;
  monitoringPlanName: string | null;
  startedAt: string | null;
  completedAt: string | null;
  previousDaySession: boolean;
}

export interface ClinicalAgentResponse {
  sessionId: number;
  nextQuestion: string | null;
  requestedField: string | null;
  missingFields: string[];
  riskLevel: RiskLevel;
  status: SessionStatus;
  conversationComplete: boolean;
}

export interface VoiceMonitoringResponse {
  transcript: string;
  agentResponse: ClinicalAgentResponse;
}

export interface ConversationTurn {
  id: string;
  speaker: "carevoice" | "patient";
  text: string;
  heard?: boolean;
}

export interface SessionHistoryTurn {
  sequenceNumber: number;
  timestamp: string;
  question: string | null;
  patientResponse: string;
  inputMode: "TEXT" | "VOICE";
  clinicalNote: string | null;
  riskLevel: RiskLevel;
}

export interface SessionHistory {
  sessionId: number;
  patientId: number;
  startedAt: string;
  status: SessionStatus;
  currentQuestion: string | null;
  conversationComplete: boolean;
  monitoringPlanName?: string | null;
  checkInDate?: string | null;
  turns: SessionHistoryTurn[];
}
