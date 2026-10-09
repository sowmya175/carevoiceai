package com.carevoice.proposal;

import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.MonitoringPlan;
import com.carevoice.domain.MonitoringPlanProposal;
import com.carevoice.domain.MonitoringPlanProposalCondition;
import com.carevoice.domain.MonitoringPlanProposalQuestion;
import com.carevoice.domain.Patient;
import com.carevoice.domain.PatientCondition;
import com.carevoice.domain.PatientMonitoringPlan;
import com.carevoice.domain.ProposalStatus;
import com.carevoice.proposal.MonitoringPlanProposalService.ProposalView;
import com.carevoice.proposal.MonitoringPlanProposalValidator.NormalizedProposal;
import com.carevoice.proposal.MonitoringPlanProposalValidator.NormalizedQuestion;
import com.carevoice.proposal.PlanGenerationModel.AllowedFieldInput;
import com.carevoice.proposal.PlanGenerationModel.ConditionInput;
import com.carevoice.proposal.PlanGenerationModel.ExistingPlanInput;
import com.carevoice.proposal.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import com.carevoice.condition.MonitoringFieldCatalogService;
import com.carevoice.repository.MonitoringPlanProposalRepository;
import com.carevoice.repository.PatientConditionRepository;
import com.carevoice.repository.PatientMonitoringPlanRepository;
import com.carevoice.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Short database transactions around plan generation.
 * The model call stays outside these methods.
 */
@Component
public class PlanProposalTransactions {
    private static final Logger log = LoggerFactory.getLogger(PlanProposalTransactions.class);

    private final MonitoringPlanProposalRepository proposals;
    private final PatientRepository patients;
    private final PatientConditionRepository conditions;
    private final PatientMonitoringPlanRepository assignments;
    private final MonitoringFieldCatalogService catalog;
    private final MonitoringPlanProposalValidator validator;
    private final ProposalViews views;

    public PlanProposalTransactions(
            MonitoringPlanProposalRepository proposals,
            PatientRepository patients,
            PatientConditionRepository conditions,
            PatientMonitoringPlanRepository assignments,
            MonitoringFieldCatalogService catalog,
            MonitoringPlanProposalValidator validator,
            ProposalViews views) {
        this.proposals = proposals;
        this.patients = patients;
        this.conditions = conditions;
        this.assignments = assignments;
        this.catalog = catalog;
        this.validator = validator;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public GenerationSnapshot read(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new IllegalArgumentException("Patient not found.");
        }
        List<PatientCondition> active = conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId).stream()
                .filter(PatientCondition::isActive)
                .sorted(Comparator.comparing(PatientCondition::isPrimaryCondition).reversed()
                        .thenComparing(PatientCondition::getConditionName))
                .toList();
        if (active.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Add an active condition before generating a suggested plan.");
        }
        List<ConditionSnapshot> snapshots = active.stream().map(ConditionSnapshot::from).toList();
        return new GenerationSnapshot(patientId, snapshots, context(patientId, active));
    }

    @Transactional
    public ProposalView persist(
            Long patientId,
            Long requestedByAccountId,
            List<ConditionSnapshot> snapshots,
            PlanGenerationResult result,
            GenerationMetadata metadata) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
        Set<Long> conditionIds = new HashSet<>();
        for (ConditionSnapshot condition : snapshots) {
            conditionIds.add(condition.id());
        }
        NormalizedProposal normalized = validator.normalize(result, conditionIds);
        for (MonitoringPlanProposal draft : proposals.findByPatient_IdAndStatus(patient.getId(), ProposalStatus.DRAFT)) {
            draft.supersede();
        }
        proposals.flush();
        MonitoringPlanProposal proposal = new MonitoringPlanProposal(patient, requestedByAccountId);
        if (metadata != null) {
            proposal.recordGeneration(
                    metadata.provider(), metadata.model(), metadata.datasetVersion(), metadata.taskContractVersion());
        }
        proposal.replaceFamilies(normalized.conditionFamilies());
        proposal.replaceWarnings(normalized.warnings());
        for (ConditionSnapshot condition : snapshots) {
            proposal.addCondition(new MonitoringPlanProposalCondition(
                    proposal, condition.id(), condition.name(), condition.category(), condition.primary()));
        }
        for (NormalizedQuestion question : normalized.questions()) {
            proposal.addQuestion(new MonitoringPlanProposalQuestion(
                    proposal,
                    question.field(),
                    question.questionText(),
                    question.displayOrder(),
                    question.required(),
                    question.rationale(),
                    question.relevantConditionIds()));
        }
        MonitoringPlanProposal saved = proposals.save(proposal);
        log.info("plan proposal generated patientId={} proposalId={} status={}",
                patient.getId(), saved.getId(), saved.getStatus());
        return views.view(saved);
    }

    private PatientPlanGenerationContext context(Long patientId, List<PatientCondition> active) {
        List<ConditionInput> inputs = new ArrayList<>();
        ConditionInput primary = null;
        for (PatientCondition condition : active) {
            ConditionInput input = new ConditionInput(
                    condition.getId(), condition.getConditionName(),
                    condition.getMonitoringCategory(), condition.isPrimaryCondition());
            inputs.add(input);
            if (condition.isPrimaryCondition()) {
                primary = input;
            }
        }
        List<AllowedFieldInput> fields = catalog.list(null, null, true).stream()
                .map(field -> new AllowedFieldInput(
                        field.code(), field.displayName(), field.description(), field.answerType(),
                        field.minimumValue(), field.maximumValue(),
                        field.options().stream().map(option -> option.code()).toList(),
                        field.categories()))
                .toList();
        boolean patientSpecific = assignments.findByPatient_IdAndActiveTrue(patientId).stream()
                .findFirst()
                .map(PatientMonitoringPlan::getMonitoringPlan)
                .map(plan -> plan.getOwnerPatientId() != null)
                .orElse(false);
        return new PatientPlanGenerationContext(patientId, inputs, primary, fields, new ExistingPlanInput(patientSpecific));
    }

    public record ConditionSnapshot(Long id, String name, MonitoringCategory category, boolean primary) {
        static ConditionSnapshot from(PatientCondition condition) {
            return new ConditionSnapshot(
                    condition.getId(), condition.getConditionName(),
                    condition.getMonitoringCategory(), condition.isPrimaryCondition());
        }
    }

    public record GenerationSnapshot(
            Long patientId, List<ConditionSnapshot> conditions, PatientPlanGenerationContext context) {}
}
