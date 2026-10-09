package com.carevoice.training;
import com.carevoice.training.PlanTrainingCatalog.CatalogMode;
import com.carevoice.training.PlanSelectionPolicy.DraftCondition;
import com.carevoice.training.PlanExample.Review;
import com.carevoice.training.PlanSelectionPolicy.Selection;
import com.carevoice.training.PlanExample.TrainingCondition;
import com.carevoice.training.PlanExample.TrainingExistingPlan;
import com.carevoice.training.PlanExample.TrainingField;
import com.carevoice.training.PlanExample.TrainingInput;
import com.carevoice.training.PlanExample.TrainingOutput;

import com.carevoice.domain.MonitoringCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Synthetic curated examples. Targets come from {@link PlanSelectionPolicy}, not from a model.
 */
public final class PlanTrainingCorpus {
    private PlanTrainingCorpus() {}

    public static Dataset build() {
        List<Spec> canonical = new ArrayList<>();
        singles(canonical);
        multis(canonical);
        List<PlanExample> examples = materialize(canonical, "cv-plan-%06d");
        List<PlanExample> challenge = materialize(challenge(), "cv-plan-c-%04d");
        assertNameGroups(examples);
        assertChallengeNamesAreNew(examples, challenge);
        return new Dataset(examples, challenge, PlanDatasetSplitter.split(examples));
    }

    private static void singles(List<Spec> out) {
        group(out, "cardiac-surgery", CatalogMode.FULL, false, heart(
                "Open-heart surgery recovery", "Open heart surgery", "Recovering from heart surgery",
                "CABG recovery", "Coronary artery bypass graft post-op", "Recovering after bypass surgery"));
        group(out, "valve-surgery", CatalogMode.FULL, false, heart(
                "Valve surgery recovery", "Recovery after heart valve surgery"));
        group(out, "knee", CatalogMode.FULL, false, ortho(
                "Total knee replacement recovery", "Knee replacement recovery",
                "Recovering after knee replacement", "Post-operative knee recovery"));
        group(out, "hip", CatalogMode.FULL, false, ortho(
                "Total hip replacement recovery", "Hip replacement recovery", "Recovering after hip surgery"));
        group(out, "shoulder", CatalogMode.FULL, false, ortho(
                "Shoulder surgery recovery", "Recovering after shoulder surgery", "Post-operative shoulder recovery"));
        group(out, "abdominal", CatalogMode.FULL, false, abdominal(
                "Abdominal surgery recovery", "Recovering after abdominal surgery",
                "Post-operative abdominal recovery", "Recovery after abdominal operation"));
        group(out, "hypertension", CatalogMode.FULL, false, htn(
                "Hypertension", "High blood pressure monitoring", "Blood pressure condition monitoring",
                "Essential hypertension monitoring", "Ongoing hypertension monitoring", "High blood pressure"));
        group(out, "type2-diabetes", CatalogMode.FULL, false, type2(
                "Type 2 diabetes", "Type II diabetes", "Type 2 diabetes daily monitoring", "Type 2 diabetes monitoring"));
        group(out, "type1-diabetes", CatalogMode.FULL, false, type1(
                "Type 1 diabetes", "Type I diabetes monitoring", "Type 1 diabetes daily monitoring"));
        group(out, "cardiac", CatalogMode.FULL, false, cardiac(
                "Cardiac condition monitoring", "Chronic cardiac condition",
                "Heart failure daily monitoring", "Ongoing cardiac monitoring"));
        group(out, "copd", CatalogMode.FULL, false, respiratory(
                "COPD", "COPD daily monitoring", "Chronic obstructive pulmonary disease monitoring"));
        group(out, "asthma", CatalogMode.FULL, false, respiratory(
                "Asthma", "Asthma daily monitoring", "Ongoing asthma monitoring"));
        group(out, "respiratory-chronic", CatalogMode.FULL, false, respiratory(
                "Chronic respiratory condition", "Ongoing respiratory condition monitoring"));
        group(out, "wellness", CatalogMode.FULL, false, wellness(false,
                "General wellness monitoring", "Routine daily wellness",
                "Routine wellness monitoring", "General health monitoring"));
        group(out, "wellness-temperature", CatalogMode.FULL, false, wellness(true,
                "General daily check-in", "Everyday wellness check-in"));
        group(out, "other", CatalogMode.FULL, false, other(
                "Unspecified symptom monitoring", "Condition not otherwise described", "Other health concern",
                "Monitoring need not categorized", "Undifferentiated recovery concern"));
        group(out, "limited-hypertension", CatalogMode.HYPERTENSION_LIMITED, false, htn(
                "Hypertension with a short field list", "High blood pressure with limited daily questions"));
        one(out, "limited-respiratory", respiratory("COPD when medication questions are unavailable"), CatalogMode.PAIN_AND_SLEEP);
        group(out, "sparse-knee", CatalogMode.MEDICATION_ONLY, false, ortho(
                "Knee replacement recovery with only medication available",
                "Knee recovery when only a medication question is available"));
    }

    private static void multis(List<Spec> out) {
        pair(out, "multi-heart-hypertension", false, heart("After open-heart surgery"), htn("Hypertensive monitoring"));
        pair(out, "multi-heart-hypertension", false, heart("Bypass surgery recovery"), htn("Raised blood pressure monitoring"));
        pair(out, "multi-heart-hypertension", true, htn("Blood pressure follow-up"), heart("Post-bypass daily recovery"));
        pair(out, "multi-heart-hypertension", false, heart("Heart valve replacement recovery"), htn("Hypertension follow-up"));

        pair(out, "multi-heart-type2", false, heart("Open chest surgery recovery"), type2("Type 2 diabetes care monitoring"));
        pair(out, "multi-heart-type2", false, heart("Recovery following heart surgery"), type2("Adult type 2 diabetes"));
        pair(out, "multi-heart-type2", true, type2("Type II diabetes follow-up"), heart("Coronary bypass recovery"));

        pair(out, "multi-knee-type2", false, ortho("Knee surgery recovery"), type2("Diabetes type 2 monitoring"));
        pair(out, "multi-knee-type2", false, ortho("After total knee replacement"), type2("Type 2 diabetes follow-up"));
        pair(out, "multi-knee-type2", true, type2("Ongoing type 2 diabetes"), ortho("Early knee replacement recovery"));

        pair(out, "multi-knee-hypertension", false, ortho("Knee operation recovery"), htn("Ongoing high blood pressure"));
        pair(out, "multi-knee-hypertension", false, ortho("Recent knee replacement recovery"), htn("High blood pressure follow-up"));
        pair(out, "multi-knee-hypertension", true, htn("Blood pressure condition follow-up"), ortho("Knee arthroplasty recovery"));

        pair(out, "multi-hip-hypertension", false, ortho("Hip surgery recovery"), htn("Hypertension daily monitoring"));
        pair(out, "multi-hip-hypertension", true, htn("Daily blood pressure monitoring"), ortho("After total hip replacement"));

        pair(out, "multi-abdominal-type2", false, abdominal("After abdominal surgery"), type2("Type 2 diabetes check-in"));
        pair(out, "multi-abdominal-type2", false, abdominal("Abdominal operation recovery"), type2("Type 2 diabetes oversight"));
        pair(out, "multi-abdominal-type2", true, type2("Type II diabetes daily context"), abdominal("Abdominal procedure recovery"));

        pair(out, "multi-abdominal-hypertension", false, abdominal("Post abdominal surgery recovery"), htn("Hypertension check-in"));
        pair(out, "multi-abdominal-hypertension", true, htn("Raised blood pressure follow-up"), abdominal("Abdominal surgery follow-up"));

        pair(out, "multi-copd-hypertension", false, respiratory("COPD follow-up"), htn("Hypertension alongside respiratory monitoring"));
        pair(out, "multi-copd-hypertension", false, respiratory("COPD symptom monitoring"), htn("High blood pressure alongside COPD"));
        pair(out, "multi-copd-hypertension", true, htn("Blood pressure monitoring with lung disease"), respiratory("COPD daily follow-up"));

        pair(out, "multi-asthma-hypertension", false, respiratory("Asthma follow-up"), htn("Hypertension with asthma monitoring"));
        pair(out, "multi-asthma-hypertension", true, htn("Hypertension with breathing monitoring"), respiratory("Asthma monitoring follow-up"));

        pair(out, "multi-cardiac-type2", false, cardiac("Cardiac follow-up monitoring"), type2("Type 2 diabetes with cardiac monitoring"));
        pair(out, "multi-cardiac-type2", false, cardiac("Chronic heart condition monitoring"), type2("Type 2 diabetes with heart monitoring"));
        pair(out, "multi-cardiac-type2", true, type2("Type 2 diabetes with a heart condition"), cardiac("Heart failure monitoring context"));

        pair(out, "multi-cardiac-hypertension", false, cardiac("Cardiac daily follow-up"), htn("Hypertension with cardiac monitoring"));
        pair(out, "multi-cardiac-hypertension", true, htn("Hypertension with a cardiac condition"), cardiac("Ongoing heart condition monitoring"));

        pair(out, "multi-wellness-hypertension", false, wellness(false, "Daily wellness monitoring"), htn("Hypertension during wellness monitoring"));
        pair(out, "multi-wellness-hypertension", false, wellness(false, "Routine wellness check-in"), htn("High blood pressure during a wellness check-in"));
        pair(out, "multi-wellness-hypertension", true, htn("Hypertension during routine wellness"), wellness(false, "Wellness check alongside blood pressure"));

        pair(out, "multi-type2-hypertension", false, type2("Type 2 diabetes and pressure monitoring"), htn("Hypertension with diabetes monitoring"));
        pair(out, "multi-type2-hypertension", true, htn("Hypertension with type 2 diabetes"), type2("Type 2 diabetes with hypertension monitoring"));
        pair(out, "multi-type2-hypertension", false, type2("Diabetes type 2 with blood pressure"), htn("Blood pressure with type 2 diabetes"));

        pair(out, "multi-shoulder-type1", false, ortho("After shoulder surgery"), type1("Type 1 diabetes follow-up"));
        pair(out, "multi-shoulder-type1", true, type1("Type I diabetes daily check-in"), ortho("Shoulder operation recovery"));

        pair(out, "multi-respiratory-cardiac", false, respiratory("Respiratory condition follow-up"), cardiac("Cardiac monitoring with a breathing condition"));
        pair(out, "multi-respiratory-cardiac", true, cardiac("Heart monitoring with a respiratory condition"), respiratory("Chronic breathing condition monitoring"));

        triple(out, "multi-heart-htn-type2", false,
                heart("Post-operative heart surgery recovery"), htn("Hypertension after heart surgery monitoring"), type2("Type 2 diabetes after heart surgery"));
        triple(out, "multi-heart-htn-type2", false,
                heart("Daily recovery after bypass"), htn("Hypertension during bypass recovery"), type2("Type 2 diabetes during bypass recovery"));
        triple(out, "multi-heart-htn-type2", true,
                type2("Type 2 diabetes as the primary daily context"), htn("Additional hypertension monitoring"), heart("Additional heart-surgery recovery"));

        triple(out, "multi-knee-htn-type2", false,
                ortho("Knee recovery with other conditions"), htn("Hypertension during knee recovery"), type2("Type 2 diabetes during knee recovery"));
        triple(out, "multi-knee-htn-type2", true,
                htn("Hypertension as the primary daily context"), ortho("Additional knee replacement recovery"), type2("Additional type 2 diabetes monitoring"));

        triple(out, "multi-copd-htn-type2", false,
                respiratory("COPD with other daily conditions"), htn("Hypertension with COPD and diabetes"), type2("Type 2 diabetes with COPD"));
        triple(out, "multi-copd-htn-type2", true,
                type2("Type 2 diabetes with lung and pressure monitoring"), respiratory("Additional COPD monitoring"), htn("Further hypertension monitoring with COPD"));

        pair(out, "multi-other-hypertension", false, other("Other unspecified concern"), htn("Hypertension with an unclear second concern"));
        pair(out, "multi-other-hypertension", true, htn("Hypertension with an uncategorized concern"), other("Uncategorized monitoring need"));

        pair(out, "multi-wellness-temp-type2", false, wellness(true, "Daily check-in with a temperature reading"), type2("Type 2 diabetes during a daily check-in"));
        pair(out, "multi-wellness-temp-type2", true, type2("Type 2 diabetes during wellness monitoring"), wellness(true, "Wellness check-in that includes temperature"));
    }

    private static List<Spec> challenge() {
        List<Spec> out = new ArrayList<>();
        triple(out, "challenge-triple", false,
                heart("Day-to-day recovery after a coronary bypass operation"),
                htn("Longstanding high blood pressure"),
                type2("Sugar diabetes, type 2"));
        triple(out, "challenge-overlap", false,
                heart("Post-surgical heart recovery"),
                htn("Blood-pressure treatment monitoring"),
                type2("Diabetes medication monitoring"));
        one(out, "challenge-incision", heart("Recovery while the surgical incision is still healing"), CatalogMode.FULL);
        one(out, "challenge-sparse-htn", htn("Elevated blood pressure follow-up"), CatalogMode.PAIN_AND_SLEEP);
        one(out, "challenge-other", other("Feeling off, no named condition"), CatalogMode.FULL);
        mismatch(out, "challenge-mismatch-postop", new DraftCondition(
                "Type 2 diabetes", MonitoringCategory.POST_OPERATIVE, "TYPE_2_DIABETES", false));
        mismatch(out, "challenge-mismatch-diabetes", new DraftCondition(
                "Replacement of a knee, now in recovery", MonitoringCategory.DIABETES, "ORTHOPEDIC_SURGERY_RECOVERY", false));
        triple(out, "challenge-reversed", true,
                type2("Adult-onset diabetes as the main daily context"),
                htn("Secondary raised blood pressure"),
                heart("Secondary recovery after a bypass operation"));
        one(out, "challenge-other-surgical", other("Sternal area after an operation"), CatalogMode.FULL);
        triple(out, "challenge-three-systems", false,
                cardiac("Weak heart daily check-in"),
                respiratory("Breathing condition, chronic"),
                type2("Diabetes, adult type"));
        one(out, "challenge-wellness", wellness(false, "Ordinary daily wellness visit"), CatalogMode.FULL);
        one(out, "challenge-sparse-knee", ortho("New knee, early recovery"), CatalogMode.MEDICATION_ONLY);
        return out;
    }

    private static List<PlanExample> materialize(List<Spec> specs, String idPattern) {
        List<PlanExample> built = new ArrayList<>();
        int sequence = 1;
        for (Spec spec : specs) {
            built.add(example(idPattern.formatted(sequence++), spec));
        }
        Map<String, Integer> groupSizes = new LinkedHashMap<>();
        for (PlanExample example : built) {
            groupSizes.merge(example.groupId(), 1, Integer::sum);
        }
        List<PlanExample> tagged = new ArrayList<>();
        for (PlanExample example : built) {
            if (groupSizes.get(example.groupId()) > 1 && !example.review().tags().contains("paraphrase")) {
                List<String> tags = new ArrayList<>(example.review().tags());
                tags.add("paraphrase");
                tagged.add(new PlanExample(example.id(), example.groupId(), example.datasetVersion(),
                        new Review(List.copyOf(tags), example.review().categoryMismatch()),
                        example.input(), example.output()));
            } else {
                tagged.add(example);
            }
        }
        return List.copyOf(tagged);
    }

    private static PlanExample example(String id, Spec spec) {
        List<TrainingField> allowed = PlanTrainingCatalog.fields(spec.catalog());
        Set<String> allowedCodes = new LinkedHashSet<>();
        for (TrainingField field : allowed) {
            allowedCodes.add(field.code());
        }
        Selection selection = PlanSelectionPolicy.select(spec.conditions(), allowedCodes, spec.mismatch());
        List<TrainingCondition> conditions = new ArrayList<>();
        for (int index = 0; index < spec.conditions().size(); index++) {
            DraftCondition condition = spec.conditions().get(index);
            conditions.add(new TrainingCondition(
                    index + 1L, condition.conditionName(), condition.category(), index == 0));
        }
        List<String> tags = tags(spec, allowedCodes, selection);
        return new PlanExample(
                id,
                spec.groupId(),
                PlanExample.DATASET_VERSION,
                new Review(tags, spec.mismatch()),
                new TrainingInput(conditions, allowed, new TrainingExistingPlan(false)),
                new TrainingOutput(selection.conditionFamilies(), selection.questions()));
    }

    private static List<String> tags(Spec spec, Set<String> allowed, Selection selection) {
        List<String> tags = new ArrayList<>();
        tags.add(spec.conditions().size() > 1 ? "multi-condition" : "single-condition");
        if (spec.reversal()) {
            tags.add("primary-reversal");
        }
        if (spec.mismatch()) {
            tags.add("category-mismatch");
        }
        long medicationSources = spec.conditions().stream()
                .filter(condition -> PlanSelectionPolicyContributes.medication(condition))
                .count();
        if (medicationSources > 1 && selection.questions().stream().anyMatch(question -> "MEDICATION_TAKEN".equals(question.fieldCode()))) {
            tags.add("dedupe");
        }
        if (allowed.size() < PlanTrainingCatalog.planAskableCodes().size()) {
            tags.add("sparse-catalog");
        }
        Selection fullCatalog = PlanSelectionPolicy.select(
                spec.conditions(), new LinkedHashSet<>(PlanTrainingCatalog.ROUTINE_TARGET_FIELDS), spec.mismatch());
        boolean droppedRelevant = fullCatalog.questions().stream()
                .anyMatch(question -> !allowed.contains(question.fieldCode()));
        boolean onlyOther = spec.conditions().stream()
                .allMatch(condition -> condition.category() == MonitoringCategory.OTHER);
        if (droppedRelevant || onlyOther) {
            tags.add("no-good-fit");
        }
        boolean omittedRoutine = PlanTrainingCatalog.ROUTINE_TARGET_FIELDS.stream()
                .anyMatch(field -> allowed.contains(field)
                        && selection.questions().stream().noneMatch(question -> field.equals(question.fieldCode())));
        if (omittedRoutine) {
            tags.add("selective");
        }
        if (spec.conditions().stream().anyMatch(PlanSelectionPolicyContributes::unsupportedTemptation)) {
            tags.add("unsupported-omission");
        }
        return List.copyOf(tags);
    }

    private static void assertNameGroups(List<PlanExample> examples) {
        Map<String, String> owner = new LinkedHashMap<>();
        for (PlanExample example : examples) {
            for (TrainingCondition condition : example.input().conditions()) {
                String previous = owner.putIfAbsent(condition.conditionName(), example.groupId());
                if (previous != null && !previous.equals(example.groupId())) {
                    throw new IllegalStateException(
                            "Condition wording appears in " + previous + " and " + example.groupId()
                                    + ": " + condition.conditionName());
                }
            }
        }
    }

    private static void assertChallengeNamesAreNew(List<PlanExample> canonical, List<PlanExample> challenge) {
        Set<String> signatures = new LinkedHashSet<>();
        for (PlanExample example : canonical) {
            for (TrainingCondition condition : example.input().conditions()) {
                signatures.add(condition.conditionName() + "|" + condition.category());
            }
        }
        for (PlanExample example : challenge) {
            for (TrainingCondition condition : example.input().conditions()) {
                if (!signatures.add(condition.conditionName() + "|" + condition.category())) {
                    throw new IllegalStateException("Challenge repeats a canonical condition and category.");
                }
            }
        }
    }

    private static void group(List<Spec> out, String groupId, CatalogMode mode, boolean mismatch, List<DraftCondition> conditions) {
        for (DraftCondition condition : conditions) {
            out.add(new Spec(groupId, List.of(condition), mode, mismatch, false));
        }
    }

    private static void one(List<Spec> out, String groupId, DraftCondition condition, CatalogMode mode) {
        out.add(new Spec(groupId, List.of(condition), mode, false, false));
    }

    private static void pair(List<Spec> out, String groupId, boolean reversal, DraftCondition primary, DraftCondition additional) {
        out.add(new Spec(groupId, List.of(primary, additional), CatalogMode.FULL, false, reversal));
    }

    private static void triple(
            List<Spec> out, String groupId, boolean reversal,
            DraftCondition primary, DraftCondition second, DraftCondition third) {
        out.add(new Spec(groupId, List.of(primary, second, third), CatalogMode.FULL, false, reversal));
    }

    private static void mismatch(List<Spec> out, String groupId, DraftCondition condition) {
        out.add(new Spec(groupId, List.of(condition), CatalogMode.FULL, true, false));
    }

    private static List<DraftCondition> heart(String... names) {
        return mapped(names, MonitoringCategory.POST_OPERATIVE, "CARDIAC_SURGERY_RECOVERY", true);
    }

    private static List<DraftCondition> ortho(String... names) {
        return mapped(names, MonitoringCategory.POST_OPERATIVE, "ORTHOPEDIC_SURGERY_RECOVERY", false);
    }

    private static List<DraftCondition> abdominal(String... names) {
        return mapped(names, MonitoringCategory.POST_OPERATIVE, "ABDOMINAL_SURGERY_RECOVERY", true);
    }

    private static List<DraftCondition> htn(String... names) {
        return mapped(names, MonitoringCategory.HYPERTENSION, "HYPERTENSION", false);
    }

    private static List<DraftCondition> type2(String... names) {
        return mapped(names, MonitoringCategory.DIABETES, "TYPE_2_DIABETES", false);
    }

    private static List<DraftCondition> type1(String... names) {
        return mapped(names, MonitoringCategory.DIABETES, "TYPE_1_DIABETES", false);
    }

    private static List<DraftCondition> cardiac(String... names) {
        return mapped(names, MonitoringCategory.CARDIAC, "CARDIAC", false);
    }

    private static List<DraftCondition> respiratory(String... names) {
        return mapped(names, MonitoringCategory.RESPIRATORY, "RESPIRATORY", false);
    }

    private static List<DraftCondition> wellness(boolean temperature, String... names) {
        return mapped(names, MonitoringCategory.WELLNESS, "GENERAL_WELLNESS", temperature);
    }

    private static List<DraftCondition> other(String... names) {
        return mapped(names, MonitoringCategory.OTHER, "OTHER", false);
    }

    private static DraftCondition heart(String name) {
        return heart(new String[] {name}).getFirst();
    }

    private static DraftCondition ortho(String name) {
        return ortho(new String[] {name}).getFirst();
    }

    private static DraftCondition abdominal(String name) {
        return abdominal(new String[] {name}).getFirst();
    }

    private static DraftCondition htn(String name) {
        return htn(new String[] {name}).getFirst();
    }

    private static DraftCondition type2(String name) {
        return type2(new String[] {name}).getFirst();
    }

    private static DraftCondition type1(String name) {
        return type1(new String[] {name}).getFirst();
    }

    private static DraftCondition cardiac(String name) {
        return cardiac(new String[] {name}).getFirst();
    }

    private static DraftCondition respiratory(String name) {
        return respiratory(new String[] {name}).getFirst();
    }

    private static DraftCondition wellness(boolean temperature, String name) {
        return wellness(temperature, new String[] {name}).getFirst();
    }

    private static DraftCondition other(String name) {
        return other(new String[] {name}).getFirst();
    }

    private static List<DraftCondition> mapped(
            String[] names, MonitoringCategory category, String family, boolean temperature) {
        List<DraftCondition> conditions = new ArrayList<>();
        for (String name : names) {
            conditions.add(new DraftCondition(name, category, family, temperature));
        }
        return conditions;
    }

    private record Spec(
            String groupId,
            List<DraftCondition> conditions,
            CatalogMode catalog,
            boolean mismatch,
            boolean reversal
    ) {}

    public record Dataset(List<PlanExample> canonical, List<PlanExample> challenge, PlanDatasetSplitter.Splits splits) {}

    /** Package-visible category contribution checks used only for review tags. */
    static final class PlanSelectionPolicyContributes {
        private PlanSelectionPolicyContributes() {}

        static boolean medication(DraftCondition condition) {
            return condition.category() != MonitoringCategory.WELLNESS;
        }

        static boolean unsupportedTemptation(DraftCondition condition) {
            return condition.category() == MonitoringCategory.POST_OPERATIVE
                    || condition.category() == MonitoringCategory.HYPERTENSION
                    || condition.category() == MonitoringCategory.DIABETES
                    || condition.category() == MonitoringCategory.CARDIAC
                    || condition.category() == MonitoringCategory.RESPIRATORY;
        }
    }
}
