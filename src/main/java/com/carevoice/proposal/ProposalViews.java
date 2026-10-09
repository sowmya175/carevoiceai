package com.carevoice.proposal;

import com.carevoice.domain.MonitoringPlan;
import com.carevoice.domain.MonitoringPlanProposal;
import com.carevoice.domain.MonitoringPlanProposalQuestion;
import com.carevoice.domain.PatientCondition;
import com.carevoice.proposal.MonitoringPlanProposalService.ConditionSnapshotView;
import com.carevoice.proposal.MonitoringPlanProposalService.ProposalView;
import com.carevoice.proposal.MonitoringPlanProposalService.QuestionView;
import com.carevoice.repository.PatientConditionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ProposalViews {
    public static final String CONDITIONS_CHANGED =
            "Patient conditions have changed since this plan was generated.";

    private final PatientConditionRepository conditions;

    public ProposalViews(PatientConditionRepository conditions) {
        this.conditions = conditions;
    }

    public ProposalView view(MonitoringPlanProposal proposal) {
        List<ConditionSnapshotView> conditionViews = proposal.getConditions().stream()
                .map(condition -> new ConditionSnapshotView(
                        condition.getSourcePatientConditionId(),
                        condition.getConditionName(),
                        condition.getMonitoringCategory().name(),
                        condition.isPrimaryCondition()))
                .toList();
        List<QuestionView> questions = proposal.getQuestions().stream()
                .sorted(Comparator.comparingInt(MonitoringPlanProposalQuestion::getDisplayOrder))
                .map(question -> new QuestionView(
                        question.getId(),
                        question.getFieldDefinition().getCode(),
                        question.getFieldDefinition().getDisplayName(),
                        question.getQuestionText(),
                        question.getDisplayOrder(),
                        question.isRequired(),
                        question.isEnabled(),
                        question.getRationale(),
                        List.copyOf(question.getRelevantConditionIds())))
                .toList();
        List<String> warnings = new ArrayList<>(proposal.getWarnings());
        if (conditionsChanged(proposal)) {
            warnings.add(CONDITIONS_CHANGED);
        }
        MonitoringPlan approved = proposal.getApprovedPlan();
        return new ProposalView(
                proposal.getId(),
                proposal.getPatient().getId(),
                proposal.getStatus().name(),
                proposal.getCreatedAt(),
                proposal.getApprovedAt(),
                proposal.getRejectedAt(),
                approved == null ? null : approved.getId(),
                approved == null ? null : approved.getName(),
                conditionViews,
                List.copyOf(proposal.getConditionFamilies()),
                List.copyOf(warnings),
                questions);
    }

    private boolean conditionsChanged(MonitoringPlanProposal proposal) {
        List<String> snapshot = proposal.getConditions().stream()
                .map(condition -> key(
                        condition.getSourcePatientConditionId(),
                        condition.getConditionName(),
                        condition.getMonitoringCategory().name(),
                        condition.isPrimaryCondition()))
                .sorted()
                .toList();
        List<String> current = conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(proposal.getPatient().getId())
                .stream()
                .filter(PatientCondition::isActive)
                .map(condition -> key(
                        condition.getId(),
                        condition.getConditionName(),
                        condition.getMonitoringCategory().name(),
                        condition.isPrimaryCondition()))
                .sorted()
                .toList();
        return !snapshot.equals(current);
    }

    private static String key(Long id, String name, String category, boolean primary) {
        return id + "|" + name + "|" + category + "|" + primary;
    }
}
