package com.carevoice.service;

import com.carevoice.agent.ClinicalAgentResponse;
import com.carevoice.agent.ClinicalExtractionService;
import com.carevoice.agent.CollectedFacts;
import com.carevoice.agent.ExtractedClinicalFacts;
import com.carevoice.agent.MissingInformationAnalyzer;
import com.carevoice.agent.MonitoringSessionContext;
import com.carevoice.agent.PlannedQuestion;
import com.carevoice.agent.QuestionPlannerAgent;
import com.carevoice.domain.MonitoringField;
import com.carevoice.plan.PlanField;
import com.carevoice.plan.SessionMonitoringPlan;
import java.util.ArrayList;
import com.carevoice.wording.DeterministicQuestionWordingService;
import com.carevoice.wording.QuestionContext;
import com.carevoice.wording.QuestionWordingService;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.observability.VoiceTiming;
import com.carevoice.repository.MonitoringSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClinicalMonitoringAgent {
    private static final Logger log = LoggerFactory.getLogger(ClinicalMonitoringAgent.class);

    private final MonitoringSessionRepository sessionRepository;
    private final ClinicalExtractionService extractionService;
    private final MonitoringSessionMerger merger;
    private final EscalationEngine escalationEngine;
    private final MissingInformationAnalyzer missingInformationAnalyzer;
    private final QuestionPlannerAgent questionPlanner;
    private final QuestionWordingService questionWording;

    public ClinicalMonitoringAgent(
            MonitoringSessionRepository sessionRepository,
            ClinicalExtractionService extractionService,
            MonitoringSessionMerger merger,
            EscalationEngine escalationEngine,
            MissingInformationAnalyzer missingInformationAnalyzer,
            QuestionPlannerAgent questionPlanner) {
        this(
                sessionRepository,
                extractionService,
                merger,
                escalationEngine,
                missingInformationAnalyzer,
                questionPlanner,
                new DeterministicQuestionWordingService());
    }

    @Autowired
    public ClinicalMonitoringAgent(
            MonitoringSessionRepository sessionRepository,
            ClinicalExtractionService extractionService,
            MonitoringSessionMerger merger,
            EscalationEngine escalationEngine,
            MissingInformationAnalyzer missingInformationAnalyzer,
            QuestionPlannerAgent questionPlanner,
            QuestionWordingService questionWording) {
        this.sessionRepository = sessionRepository;
        this.extractionService = extractionService;
        this.merger = merger;
        this.escalationEngine = escalationEngine;
        this.missingInformationAnalyzer = missingInformationAnalyzer;
        this.questionPlanner = questionPlanner;
        this.questionWording = questionWording;
    }

    @Transactional
    public ClinicalAgentResponse processMessage(Long sessionId, String patientMessage) {
        MonitoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring session not found: " + sessionId));
        String previousQuestion = session.getNextQuestion();
        AgentTurn turn = processTurn(sessionId, patientMessage);
        return present(sessionId, previousQuestion, patientMessage, turn.response());
    }

    @Transactional
    public AgentTurn processTurn(Long sessionId, String patientMessage) {
        MonitoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring session not found: " + sessionId));

        MonitoringSessionContext extractionContext = MonitoringSessionContext.from(session);
        ExtractedClinicalFacts extracted = extractionService.extract(patientMessage, extractionContext);
        long planningStarted = System.nanoTime();
        try {
            return planTurn(session, patientMessage, extractionContext, extracted);
        } finally {
            VoiceTiming.log(log, "planningMs=" + VoiceTiming.millisSince(planningStarted) + " sessionId=" + sessionId);
        }
    }

    /**
     * Applies optional adaptive wording after the monitoring transaction has committed.
     * Field selection has already happened. Clarification text is left unchanged.
     */
    public ClinicalAgentResponse present(
            Long sessionId,
            String previousQuestion,
            String patientMessage,
            ClinicalAgentResponse response) {
        if (response.conversationComplete() || response.nextQuestion() == null || response.requestedField() == null
                || QuestionClarification.question(response.requestedField()).equals(response.nextQuestion())) {
            VoiceTiming.log(log, "adaptiveWordingMs=0 sessionId=" + sessionId);
            return response;
        }
        long started = System.nanoTime();
        String displayed = questionWording.generateQuestion(
                new PlannedQuestion(response.requestedField(), response.nextQuestion()),
                new QuestionContext(previousQuestion, patientMessage, response.collectedFacts()));
        VoiceTiming.log(log, "adaptiveWordingMs=" + VoiceTiming.millisSince(started) + " sessionId=" + sessionId);
        if (displayed == null || displayed.isBlank() || displayed.equals(response.nextQuestion())) {
            return response;
        }
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setNextQuestion(displayed);
            sessionRepository.save(session);
        });
        return new ClinicalAgentResponse(
                response.sessionId(),
                displayed,
                response.requestedField(),
                response.missingFields(),
                response.riskLevel(),
                response.status(),
                response.conversationComplete(),
                response.collectedFacts());
    }

    private AgentTurn planTurn(
            MonitoringSession session,
            String patientMessage,
            MonitoringSessionContext extractionContext,
            ExtractedClinicalFacts extracted) {
        session.setLatestTranscript(patientMessage);
        merger.merge(session, extracted);

        EscalationEngine.Evaluation evaluation = escalationEngine.evaluate(session);
        session.setRiskLevel(evaluation.riskLevel());
        session.setEscalationReason(evaluation.reason());

        CollectedFacts collected = CollectedFacts.from(session);
        List<PlanField> sessionPlan = SessionMonitoringPlan.fields(session);
        MonitoringField previousField = extractionContext.previouslyRequestedField();
        List<MonitoringField> missing = new ArrayList<>(missingInformationAnalyzer.missingFields(collected, sessionPlan));
        // Explicitly directed optional fields need the same clarification path as routine fields.
        if (previousField != null && QuestionClarification.isUnknown(previousField, collected)
                && !missing.contains(previousField)) {
            missing.add(previousField);
        }

        if (evaluation.riskLevel() == RiskLevel.RED) {
            return complete(session, missing, SessionStatus.READY_FOR_REVIEW, extracted);
        }

        if (previousField != null && QuestionClarification.isUnknown(previousField, collected)
                && !session.isDeferred(previousField)) {
            if (session.getClarifiedField() != previousField
                    && !QuestionClarification.question(previousField).equals(session.getNextQuestion())) {
                session.setClarifiedField(previousField);
                return ask(session, previousField, QuestionClarification.question(previousField), missing, extracted);
            }
            session.deferField(previousField);
        }
        session.setClarifiedField(null);
        List<MonitoringField> plannable = missing.stream().filter(field -> !session.isDeferred(field)).toList();
        var next = questionPlanner.plan(MonitoringSessionContext.from(session), plannable, evaluation, sessionPlan);
        if (next.isEmpty()) {
            SessionStatus status = evaluation.riskLevel() == RiskLevel.GREEN
                    ? SessionStatus.COMPLETED
                    : SessionStatus.READY_FOR_REVIEW;
            return complete(session, missing, status, extracted);
        }

        PlannedQuestion question = next.get();
        return ask(session, question.field(), question.question(), missing, extracted);
    }

    private AgentTurn ask(MonitoringSession session, MonitoringField field, String displayedQuestion,
                          List<MonitoringField> missing, ExtractedClinicalFacts extracted) {
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setNextQuestion(displayedQuestion);
        session.setRequestedField(field);
        session.setQuestionsAsked(session.getQuestionsAsked() + 1);
        sessionRepository.save(session);
        return new AgentTurn(new ClinicalAgentResponse(
                session.getId(),
                displayedQuestion,
                field,
                missing,
                session.getRiskLevel(),
                session.getStatus(),
                false,
                CollectedFacts.from(session)
        ), extracted);
    }

    private AgentTurn complete(MonitoringSession session, List<MonitoringField> missing, SessionStatus status, ExtractedClinicalFacts extracted) {
        session.setStatus(status);
        session.setNextQuestion(null);
        session.setRequestedField(null);
        sessionRepository.save(session);
        return new AgentTurn(new ClinicalAgentResponse(
                session.getId(),
                null,
                null,
                missing,
                session.getRiskLevel(),
                session.getStatus(),
                true,
                CollectedFacts.from(session)
        ), extracted);
    }
}
