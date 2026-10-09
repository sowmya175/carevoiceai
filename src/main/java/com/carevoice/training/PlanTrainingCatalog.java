package com.carevoice.training;

import com.carevoice.domain.MonitoringAnswerType;
import com.carevoice.domain.MonitoringCategory;
import com.carevoice.training.PlanExample.TrainingField;

import java.util.List;
import java.util.Set;

import static com.carevoice.domain.MonitoringCategory.CARDIAC;
import static com.carevoice.domain.MonitoringCategory.DIABETES;
import static com.carevoice.domain.MonitoringCategory.HYPERTENSION;
import static com.carevoice.domain.MonitoringCategory.POST_OPERATIVE;
import static com.carevoice.domain.MonitoringCategory.RESPIRATORY;
import static com.carevoice.domain.MonitoringCategory.WELLNESS;

/**
 * Snapshot of the plan-askable catalog for dataset version {@code carevoice-plan-generation-v1}.
 * A future catalog change needs a new dataset version.
 */
public final class PlanTrainingCatalog {
    public static final List<String> ROUTINE_TARGET_FIELDS = List.of(
            "PAIN_SCORE", "MEDICATION_TAKEN", "APPETITE", "SLEEP_QUALITY", "TEMPERATURE");

    /** Plan-askable follow-ups. They may appear in allowedFields and are not routine targets. */
    public static final List<String> FOLLOW_UP_FIELDS = List.of(
            "DIZZINESS_ONSET", "LOSS_OF_CONSCIOUSNESS");

    public static final List<String> NOT_PLAN_ASKABLE = List.of(
            "DIZZINESS", "SHORTNESS_OF_BREATH",
            "INCISION_STATUS", "SWELLING", "MOBILITY", "ACTIVITY_TOLERANCE",
            "BLOOD_PRESSURE", "GLUCOSE", "WEIGHT", "HEART_RATE", "OXYGEN_SATURATION");

    public static final List<String> CONDITION_FAMILIES = List.of(
            "GENERAL_WELLNESS",
            "CARDIAC_SURGERY_RECOVERY",
            "ORTHOPEDIC_SURGERY_RECOVERY",
            "ABDOMINAL_SURGERY_RECOVERY",
            "HYPERTENSION",
            "TYPE_1_DIABETES",
            "TYPE_2_DIABETES",
            "CARDIAC",
            "RESPIRATORY",
            "OTHER");

    private static final List<TrainingField> FIELDS = List.of(
            field("PAIN_SCORE", "Pain score",
                    "Pain score already collected by the daily check-in, from 0 to 10.",
                    MonitoringAnswerType.NUMBER, 0.0, 10.0, List.of(),
                    List.of(WELLNESS, POST_OPERATIVE)),
            field("MEDICATION_TAKEN", "Medication taken",
                    "Whether the patient reported taking prescribed medication.",
                    MonitoringAnswerType.BOOLEAN, null, null, List.of(),
                    List.of(POST_OPERATIVE, HYPERTENSION, DIABETES, CARDIAC, RESPIRATORY)),
            field("APPETITE", "Appetite",
                    "Appetite word already accepted by the daily check-in.",
                    MonitoringAnswerType.SINGLE_CHOICE, null, null,
                    List.of("GOOD", "NORMAL", "REDUCED", "POOR"),
                    List.of(WELLNESS, POST_OPERATIVE, DIABETES)),
            field("SLEEP_QUALITY", "Sleep",
                    "Sleep word already accepted by the daily check-in.",
                    MonitoringAnswerType.SINGLE_CHOICE, null, null,
                    List.of("GOOD", "NORMAL", "POOR"),
                    List.of(WELLNESS, POST_OPERATIVE)),
            field("TEMPERATURE", "Temperature",
                    "Temperature number already stored by the daily check-in. No unit is authoritative.",
                    MonitoringAnswerType.NUMBER, 30.0, 115.0, List.of(),
                    List.of(WELLNESS, POST_OPERATIVE)),
            field("DIZZINESS_ONSET", "Dizziness onset",
                    "When dizziness started, as free text already stored by the daily check-in.",
                    MonitoringAnswerType.TEXT, null, null, List.of(),
                    List.of(CARDIAC, POST_OPERATIVE)),
            field("LOSS_OF_CONSCIOUSNESS", "Loss of consciousness",
                    "Whether loss of consciousness was reported.",
                    MonitoringAnswerType.BOOLEAN, null, null, List.of(),
                    List.of(CARDIAC, POST_OPERATIVE)));

    private PlanTrainingCatalog() {}

    public static List<String> planAskableCodes() {
        return FIELDS.stream().map(TrainingField::code).toList();
    }

    public static List<TrainingField> fullCatalog() {
        return FIELDS;
    }

    public static List<TrainingField> fields(CatalogMode mode) {
        return switch (mode) {
            case FULL -> FIELDS;
            case HYPERTENSION_LIMITED -> pick("MEDICATION_TAKEN", "SLEEP_QUALITY", "PAIN_SCORE", "APPETITE");
            case PAIN_AND_SLEEP -> pick("PAIN_SCORE", "SLEEP_QUALITY");
            case MEDICATION_ONLY -> pick("MEDICATION_TAKEN");
        };
    }

    public static TrainingField definition(String code) {
        return FIELDS.stream().filter(field -> field.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown training field."));
    }

    private static List<TrainingField> pick(String... codes) {
        return java.util.Arrays.stream(codes).map(PlanTrainingCatalog::definition).toList();
    }

    private static TrainingField field(
            String code, String displayName, String description, MonitoringAnswerType answerType,
            Double minimum, Double maximum, List<String> allowedValues, List<MonitoringCategory> categories) {
        return new TrainingField(code, displayName, description, answerType, minimum, maximum, allowedValues, categories);
    }

    public enum CatalogMode {
        FULL,
        HYPERTENSION_LIMITED,
        PAIN_AND_SLEEP,
        MEDICATION_ONLY
    }

    public static Set<String> notPlanAskable() {
        return Set.copyOf(NOT_PLAN_ASKABLE);
    }
}
