package com.carevoice.proposal;

import com.carevoice.condition.MonitoringFieldCatalogService;
import com.carevoice.domain.MonitoringFieldDefinition;
import com.carevoice.proposal.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Blocks a model result that would activate an unknown or non-askable field.
 * Duplicate field codes stay with {@link MonitoringPlanProposalValidator}, which keeps one question.
 */
@Component
public class PlanOutputGate {
    private final java.util.function.Predicate<String> askable;

    @Autowired
    public PlanOutputGate(MonitoringFieldCatalogService catalog) {
        this.askable = code -> {
            MonitoringFieldDefinition field = catalog.findByCode(code).orElse(null);
            return field != null && MonitoringFieldCatalogService.planAskable(field);
        };
    }

    PlanOutputGate(java.util.Set<String> askableCodes) {
        this.askable = askableCodes::contains;
    }

    public void assertExecutable(PlanGenerationResult result) {
        if (result == null || result.questions() == null || result.questions().isEmpty()) {
            throw new PlanGenerationFailedException();
        }
        for (GeneratedQuestion question : result.questions()) {
            String code = question.fieldCode() == null ? "" : question.fieldCode().trim();
            String text = question.questionText() == null ? "" : question.questionText().trim();
            if (code.isBlank() || text.isBlank() || !askable.test(code)) {
                throw new PlanGenerationFailedException();
            }
        }
    }
}
