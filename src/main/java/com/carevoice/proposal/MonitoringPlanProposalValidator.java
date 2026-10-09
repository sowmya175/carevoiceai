package com.carevoice.proposal;

import com.carevoice.condition.MonitoringFieldCatalogService;
import com.carevoice.domain.MonitoringFieldDefinition;
import com.carevoice.proposal.PlanGenerationModel.GeneratedQuestion;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative check before a suggestion is stored or approved.
 * The model may select catalog fields. It may not create fields or change their meaning.
 */
@Component
public class MonitoringPlanProposalValidator {
    static final int MAX_TEXT = 500;
    private static final int MAX_FAMILIES = 12;

    private final MonitoringFieldCatalogService catalog;

    public MonitoringPlanProposalValidator(MonitoringFieldCatalogService catalog) {
        this.catalog = catalog;
    }

    public NormalizedProposal normalize(PlanGenerationResult result, Set<Long> conditionIds) {
        Map<String, MonitoringFieldDefinition> fields = new LinkedHashMap<>();
        for (MonitoringFieldDefinition field : catalog.all()) {
            fields.put(field.getCode(), field);
        }
        List<String> warnings = new ArrayList<>();
        Map<String, DraftQuestion> kept = new LinkedHashMap<>();
        List<GeneratedQuestion> incoming = result == null || result.questions() == null
                ? List.of() : result.questions();
        for (GeneratedQuestion question : incoming) {
            String code = question.fieldCode() == null ? "" : question.fieldCode().trim();
            MonitoringFieldDefinition field = fields.get(code);
            if (field == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "The suggestion included a field CareVoice does not recognize.");
            }
            if (!MonitoringFieldCatalogService.planAskable(field)) {
                String warning = code + " is not available for daily questions yet.";
                if (!warnings.contains(warning)) {
                    warnings.add(warning);
                }
                continue;
            }
            DraftQuestion existing = kept.get(code);
            if (existing == null) {
                kept.put(code, new DraftQuestion(field, question, conditionIds));
            } else {
                existing.merge(question, conditionIds);
            }
        }
        if (kept.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The suggestion did not include a usable daily question.");
        }
        List<NormalizedQuestion> questions = new ArrayList<>();
        int order = 1;
        for (DraftQuestion draft : kept.values()) {
            String text = draft.text == null ? "" : draft.text.trim();
            if (text.isBlank() || text.length() > MAX_TEXT) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "A suggested question did not include usable wording.");
            }
            questions.add(new NormalizedQuestion(
                    draft.field, text, draft.required, order++, List.copyOf(draft.relevantIds), blankToNull(draft.rationale)));
        }
        return new NormalizedProposal(families(result), questions, List.copyOf(warnings));
    }

    public void assertApprovable(List<com.carevoice.domain.MonitoringPlanProposalQuestion> questions) {
        Set<String> codes = new LinkedHashSet<>();
        boolean requiredEnabled = false;
        for (com.carevoice.domain.MonitoringPlanProposalQuestion question : questions) {
            if (!question.isEnabled()) {
                continue;
            }
            MonitoringFieldDefinition field = question.getFieldDefinition();
            if (!MonitoringFieldCatalogService.planAskable(field) || question.getDisplayOrder() < 1) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "A question uses a field that is not available for daily check-ins.");
            }
            String text = question.getQuestionText() == null ? "" : question.getQuestionText().trim();
            if (text.isBlank() || text.length() > MAX_TEXT) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "A suggested question did not include usable wording.");
            }
            if (!codes.add(field.getCode())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Keep one question for each monitoring field.");
            }
            if (question.isRequired()) {
                requiredEnabled = true;
            }
        }
        if (!requiredEnabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Keep at least one required daily question.");
        }
    }

    public static String cleanText(String questionText) {
        String text = questionText == null ? "" : questionText.trim();
        if (text.isBlank() || text.length() > MAX_TEXT) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A suggested question did not include usable wording.");
        }
        return text;
    }

    private static List<String> families(PlanGenerationResult result) {
        List<String> families = new ArrayList<>();
        if (result == null || result.conditionFamilies() == null) {
            return families;
        }
        for (String family : result.conditionFamilies()) {
            if (family == null) {
                continue;
            }
            String token = family.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (token.matches("[A-Z0-9_]{1,80}") && !families.contains(token)) {
                families.add(token);
            }
            if (families.size() == MAX_FAMILIES) {
                break;
            }
        }
        return families;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_TEXT ? trimmed.substring(0, MAX_TEXT) : trimmed;
    }

    public record NormalizedProposal(
            List<String> conditionFamilies,
            List<NormalizedQuestion> questions,
            List<String> warnings
    ) {}

    public record NormalizedQuestion(
            MonitoringFieldDefinition field,
            String questionText,
            boolean required,
            int displayOrder,
            List<Long> relevantConditionIds,
            String rationale
    ) {}

    private static final class DraftQuestion {
        private final MonitoringFieldDefinition field;
        private String text;
        private boolean required;
        private String rationale;
        private final Set<Long> relevantIds = new LinkedHashSet<>();

        private DraftQuestion(MonitoringFieldDefinition field, GeneratedQuestion question, Set<Long> conditionIds) {
            this.field = field;
            this.text = question.questionText();
            this.required = question.required();
            this.rationale = question.rationale();
            addIds(question.relevantConditionIds(), conditionIds);
        }

        private void merge(GeneratedQuestion question, Set<Long> conditionIds) {
            if ((text == null || text.isBlank()) && question.questionText() != null) {
                text = question.questionText();
            }
            required = required || question.required();
            if ((rationale == null || rationale.isBlank()) && question.rationale() != null) {
                rationale = question.rationale();
            }
            addIds(question.relevantConditionIds(), conditionIds);
        }

        private void addIds(List<Long> ids, Set<Long> conditionIds) {
            if (ids == null) {
                return;
            }
            for (Long id : ids) {
                if (id != null && conditionIds.contains(id)) {
                    relevantIds.add(id);
                }
            }
        }
    }
}
