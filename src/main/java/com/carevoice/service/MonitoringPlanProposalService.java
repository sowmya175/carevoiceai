package com.carevoice.service;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.mapper.ProposalViews;

import com.carevoice.domain.UserAccount;

import com.carevoice.domain.MonitoringFieldDefinition;
import com.carevoice.domain.MonitoringPlan;
import com.carevoice.domain.MonitoringPlanProposal;
import com.carevoice.domain.MonitoringPlanProposalQuestion;
import com.carevoice.domain.Patient;
import com.carevoice.domain.PatientCondition;
import com.carevoice.domain.ProposalStatus;

import com.carevoice.domain.PlanField;

import com.carevoice.dto.proposal.ProposalSummary;
import com.carevoice.dto.proposal.ProposalView;
import com.carevoice.repository.MonitoringPlanProposalRepository;
import com.carevoice.repository.MonitoringPlanQuestionRepository;
import com.carevoice.repository.MonitoringPlanRepository;
import com.carevoice.repository.PatientConditionRepository;
import com.carevoice.repository.PatientRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MonitoringPlanProposalService {
    private static final Logger log = LoggerFactory.getLogger(MonitoringPlanProposalService.class);
    static final String PLAN_NAME = "Personalized Daily Monitoring Plan";

    private final MonitoringPlanProposalRepository proposals;
    private final PatientRepository patients;
    private final PatientConditionRepository conditions;
    private final MonitoringFieldCatalogService catalog;
    private final ActivePlanGenerationModel model;
    private final PlanProposalTransactions transactions;
    private final ProposalViews views;
    private final PlanGenerationTrace trace;
    private final MonitoringPlanProposalValidator validator;
    private final MonitoringPlanRepository plans;
    private final MonitoringPlanQuestionRepository planQuestions;
    private final MonitoringPlanService monitoringPlans;

    public MonitoringPlanProposalService(
            MonitoringPlanProposalRepository proposals,
            PatientRepository patients,
            PatientConditionRepository conditions,
            MonitoringFieldCatalogService catalog,
            ActivePlanGenerationModel model,
            PlanProposalTransactions transactions,
            ProposalViews views,
            PlanGenerationTrace trace,
            MonitoringPlanProposalValidator validator,
            MonitoringPlanRepository plans,
            MonitoringPlanQuestionRepository planQuestions,
            MonitoringPlanService monitoringPlans) {
        this.proposals = proposals;
        this.patients = patients;
        this.conditions = conditions;
        this.catalog = catalog;
        this.model = model;
        this.transactions = transactions;
        this.views = views;
        this.trace = trace;
        this.validator = validator;
        this.plans = plans;
        this.planQuestions = planQuestions;
        this.monitoringPlans = monitoringPlans;
    }

    public ProposalView generate(Long patientId, UserAccount requestedBy) {
        PlanProposalTransactions.GenerationSnapshot snapshot = transactions.read(patientId);
        PlanGenerationResult result;
        try {
            result = model.generate(snapshot.context());
        } catch (ResponseStatusException ex) {
            trace.readAndClear();
            throw ex;
        } catch (RuntimeException ex) {
            trace.readAndClear();
            log.warn("plan proposal generation failed patientId={} errorType={}",
                    patientId, ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create a suggested plan.");
        }
        GenerationMetadata metadata = trace.readAndClear();
        return transactions.persist(
                snapshot.patientId(), requestedBy.getId(), snapshot.conditions(), result, metadata);
    }

    /**
     * Stores a model result after validation. Used by generation and by tests that supply a result directly.
     * A rejected result does not create a proposal and does not change the assigned plan.
     */
    @Transactional
    public ProposalView store(Long patientId, Long requestedByAccountId, PlanGenerationResult result) {
        if (!patients.existsById(patientId)) {
            throw new IllegalArgumentException("Patient not found.");
        }
        List<PlanProposalTransactions.ConditionSnapshot> snapshots = lockedActive(patientId).stream()
                .map(PlanProposalTransactions.ConditionSnapshot::from)
                .toList();
        return transactions.persist(patientId, requestedByAccountId, snapshots, result, null);
    }

    @Transactional(readOnly = true)
    public List<ProposalSummary> list(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new IllegalArgumentException("Patient not found.");
        }
        return proposals.findByPatient_IdOrderByCreatedAtDesc(patientId).stream()
                .map(proposal -> new ProposalSummary(
                        proposal.getId(), proposal.getPatient().getId(), proposal.getStatus().name(), proposal.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProposalView get(Long proposalId) {
        return present(load(proposalId));
    }

    @Transactional
    public ProposalView updateQuestion(Long proposalId, Long questionId, String questionText, Boolean required, Boolean enabled) {
        MonitoringPlanProposal proposal = lockDraft(proposalId);
        MonitoringPlanProposalQuestion question = question(proposal, questionId);
        boolean nextRequired = required == null ? question.isRequired() : required;
        boolean nextEnabled = enabled == null ? question.isEnabled() : enabled;
        question.revise(MonitoringPlanProposalValidator.cleanText(questionText == null
                ? question.getQuestionText() : questionText), nextRequired, nextEnabled);
        requireEnabledRequired(proposal);
        log.info("plan proposal question updated patientId={} proposalId={} status={}",
                proposal.getPatient().getId(), proposal.getId(), proposal.getStatus());
        return present(proposal);
    }

    @Transactional
    public ProposalView addQuestion(Long proposalId, String fieldCode, String questionText, boolean required) {
        MonitoringPlanProposal proposal = lockDraft(proposalId);
        String code = fieldCode == null ? "" : fieldCode.trim();
        MonitoringFieldDefinition field = catalog.findByCode(code)
                .filter(MonitoringFieldCatalogService::planAskable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Choose a supported monitoring field."));
        boolean used = proposal.getQuestions().stream()
                .anyMatch(question -> question.getFieldDefinition().getId().equals(field.getId()));
        if (used) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That field is already on this suggestion.");
        }
        int order = proposal.getQuestions().stream()
                .mapToInt(MonitoringPlanProposalQuestion::getDisplayOrder).max().orElse(0) + 1;
        MonitoringPlanProposalQuestion question = new MonitoringPlanProposalQuestion(
                proposal, field, MonitoringPlanProposalValidator.cleanText(questionText), order, required, null, List.of());
        proposal.addQuestion(question);
        if (!required) {
            requireEnabledRequired(proposal);
        }
        return present(proposal);
    }

    @Transactional
    public ProposalView reorder(Long proposalId, List<Long> questionIds) {
        MonitoringPlanProposal proposal = lockDraft(proposalId);
        List<MonitoringPlanProposalQuestion> questions = proposal.getQuestions();
        if (questionIds == null || questionIds.size() != questions.size()
                || !new HashSet<>(questionIds).equals(questions.stream().map(MonitoringPlanProposalQuestion::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Choose the full question order.");
        }
        int order = 1;
        for (Long questionId : questionIds) {
            question(proposal, questionId).place(order++);
        }
        return present(proposal);
    }

    @Transactional
    public ProposalView approve(Long proposalId, UserAccount clinician) {
        MonitoringPlanProposal proposal = lockDraft(proposalId);
        validator.assertApprovable(proposal.getQuestions());
        Patient patient = proposal.getPatient();
        MonitoringPlan plan = new MonitoringPlan(
                "PATIENT_" + patient.getId() + "_PROPOSAL_" + proposal.getId(),
                PLAN_NAME,
                "Clinician-approved daily monitoring questions for this patient. "
                        + "This plan is decision support and is not a diagnosis or treatment recommendation.",
                "Personalized");
        plan.assignOwner(patient.getId());
        plans.save(plan);
        int order = 1;
        for (MonitoringPlanProposalQuestion question : proposal.getQuestions().stream()
                .sorted(Comparator.comparingInt(MonitoringPlanProposalQuestion::getDisplayOrder))
                .toList()) {
            if (!question.isEnabled()) {
                continue;
            }
            planQuestions.save(new com.carevoice.domain.MonitoringPlanQuestion(plan, new PlanField(
                    question.getFieldDefinition().getLegacyField(),
                    question.getQuestionText(),
                    QuestionClarification.question(question.getFieldDefinition().getLegacyField()),
                    order++,
                    question.isRequired())));
        }
        monitoringPlans.assign(patient.getId(), plan.getId(), clinician);
        proposal.approve(plan);
        log.info("plan proposal approved patientId={} proposalId={} status={}",
                patient.getId(), proposal.getId(), proposal.getStatus());
        return present(proposal);
    }

    @Transactional
    public ProposalView reject(Long proposalId) {
        MonitoringPlanProposal proposal = lockDraft(proposalId);
        proposal.reject();
        log.info("plan proposal rejected patientId={} proposalId={} status={}",
                proposal.getPatient().getId(), proposal.getId(), proposal.getStatus());
        return present(proposal);
    }

    private ProposalView present(MonitoringPlanProposal proposal) {
        return views.view(proposal, currentActive(proposal.getPatient().getId()));
    }

    private List<PatientCondition> currentActive(Long patientId) {
        return conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId).stream()
                .filter(PatientCondition::isActive)
                .toList();
    }

    private List<PatientCondition> lockedActive(Long patientId) {
        List<PatientCondition> active = conditions.lockByPatientId(patientId).stream()
                .filter(PatientCondition::isActive)
                .sorted(Comparator.comparing(PatientCondition::isPrimaryCondition).reversed()
                        .thenComparing(PatientCondition::getConditionName))
                .toList();
        if (active.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Add an active condition before generating a suggested plan.");
        }
        return active;
    }

    private MonitoringPlanProposal lockDraft(Long proposalId) {
        MonitoringPlanProposal proposal = proposals.lockById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found."));
        if (proposal.getStatus() != ProposalStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That suggestion can no longer be changed.");
        }
        return proposal;
    }

    private MonitoringPlanProposal load(Long proposalId) {
        return proposals.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found."));
    }

    private static MonitoringPlanProposalQuestion question(MonitoringPlanProposal proposal, Long questionId) {
        return proposal.getQuestions().stream()
                .filter(question -> question.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found."));
    }

    private static void requireEnabledRequired(MonitoringPlanProposal proposal) {
        boolean ready = proposal.getQuestions().stream().anyMatch(question -> question.isEnabled() && question.isRequired());
        if (!ready) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Keep at least one required daily question.");
        }
    }
}
