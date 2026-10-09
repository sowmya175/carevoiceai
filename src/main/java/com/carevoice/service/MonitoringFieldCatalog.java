package com.carevoice.service;

import com.carevoice.domain.MonitoringAnswerType;
import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringFieldRuntimeSupport;

import java.util.List;
import java.util.Set;

/**
 * Initial catalog. Category tags describe future plan generation.
 * They do not select today's questions.
 * Choice labels are demo vocabulary, not a validated assessment.
 */
final class MonitoringFieldCatalog {
    private MonitoringFieldCatalog() {}

    static List<Seed> seeds() {
        return List.of(
                supported("PAIN_SCORE", "Pain score",
                        "Pain score already collected by the daily check-in, from 0 to 10.",
                        MonitoringAnswerType.NUMBER, null, 0.0, 10.0, MonitoringField.PAIN_SCORE,
                        Set.of(MonitoringCategory.WELLNESS, MonitoringCategory.POST_OPERATIVE)),
                supported("MEDICATION_TAKEN", "Medication taken",
                        "Whether the patient reported taking prescribed medication.",
                        MonitoringAnswerType.BOOLEAN, null, null, null, MonitoringField.MEDICATION_TAKEN,
                        Set.of(MonitoringCategory.POST_OPERATIVE, MonitoringCategory.HYPERTENSION,
                                MonitoringCategory.DIABETES, MonitoringCategory.CARDIAC, MonitoringCategory.RESPIRATORY)),
                choice("APPETITE", "Appetite",
                        "Appetite word already accepted by the daily check-in.",
                        MonitoringFieldRuntimeSupport.SUPPORTED, MonitoringField.APPETITE,
                        Set.of(MonitoringCategory.WELLNESS, MonitoringCategory.POST_OPERATIVE, MonitoringCategory.DIABETES),
                        List.of(option("GOOD", "Good", 1), option("NORMAL", "Normal", 2),
                                option("REDUCED", "Reduced", 3), option("POOR", "Poor", 4))),
                choice("SLEEP_QUALITY", "Sleep",
                        "Sleep word already accepted by the daily check-in.",
                        MonitoringFieldRuntimeSupport.SUPPORTED, MonitoringField.SLEEP_QUALITY,
                        Set.of(MonitoringCategory.WELLNESS, MonitoringCategory.POST_OPERATIVE),
                        List.of(option("GOOD", "Good", 1), option("NORMAL", "Normal", 2), option("POOR", "Poor", 3))),
                supported("TEMPERATURE", "Temperature",
                        "Temperature number already stored by the daily check-in. No unit is authoritative.",
                        MonitoringAnswerType.NUMBER, null, 30.0, 115.0, MonitoringField.TEMPERATURE,
                        Set.of(MonitoringCategory.WELLNESS, MonitoringCategory.POST_OPERATIVE)),
                supported("DIZZINESS", "Dizziness",
                        "Whether dizziness was reported. Stored by the current extraction flow.",
                        MonitoringAnswerType.BOOLEAN, null, null, null, null,
                        Set.of(MonitoringCategory.CARDIAC, MonitoringCategory.POST_OPERATIVE)),
                supported("DIZZINESS_ONSET", "Dizziness onset",
                        "When dizziness started, as free text already stored by the daily check-in.",
                        MonitoringAnswerType.TEXT, null, null, null, MonitoringField.DIZZINESS_ONSET,
                        Set.of(MonitoringCategory.CARDIAC, MonitoringCategory.POST_OPERATIVE)),
                supported("LOSS_OF_CONSCIOUSNESS", "Loss of consciousness",
                        "Whether loss of consciousness was reported.",
                        MonitoringAnswerType.BOOLEAN, null, null, null, MonitoringField.LOSS_OF_CONSCIOUSNESS,
                        Set.of(MonitoringCategory.CARDIAC, MonitoringCategory.POST_OPERATIVE)),
                supported("SHORTNESS_OF_BREATH", "Shortness of breath",
                        "Whether shortness of breath was reported. Stored by the current extraction flow.",
                        MonitoringAnswerType.BOOLEAN, null, null, null, null,
                        Set.of(MonitoringCategory.CARDIAC, MonitoringCategory.RESPIRATORY, MonitoringCategory.POST_OPERATIVE)),
                choice("INCISION_STATUS", "Incision status",
                        "Demo vocabulary for a future post-operative field. Not a validated wound assessment.",
                        MonitoringFieldRuntimeSupport.CATALOG_ONLY, null,
                        Set.of(MonitoringCategory.POST_OPERATIVE),
                        List.of(option("NORMAL", "Normal", 1), option("REDNESS", "Redness", 2),
                                option("SWELLING", "Swelling", 3), option("DRAINAGE", "Drainage", 4),
                                option("OTHER", "Other", 5))),
                supported("SWELLING", "Swelling",
                        "Future catalog field. The daily check-in cannot ask this yet.",
                        MonitoringAnswerType.BOOLEAN, null, null, null, null,
                        Set.of(MonitoringCategory.POST_OPERATIVE))
                        .support(MonitoringFieldRuntimeSupport.CATALOG_ONLY),
                choice("MOBILITY", "Mobility",
                        "Demo vocabulary for a future mobility field. Not a validated functional assessment.",
                        MonitoringFieldRuntimeSupport.CATALOG_ONLY, null,
                        Set.of(MonitoringCategory.POST_OPERATIVE),
                        List.of(option("LIMITED", "Limited", 1), option("WITH_ASSISTANCE", "With assistance", 2),
                                option("USUAL", "Usual for this patient", 3))),
                choice("ACTIVITY_TOLERANCE", "Activity tolerance",
                        "Demo vocabulary for a future activity field. Not a validated exertion assessment.",
                        MonitoringFieldRuntimeSupport.CATALOG_ONLY, null,
                        Set.of(MonitoringCategory.POST_OPERATIVE),
                        List.of(option("REDUCED", "Reduced", 1), option("USUAL", "Usual for this patient", 2)))
        );
    }

    private static Seed supported(
            String code, String displayName, String description, MonitoringAnswerType answerType,
            String unit, Double minimum, Double maximum, MonitoringField legacy, Set<MonitoringCategory> categories) {
        return new Seed(code, displayName, description, answerType, unit, minimum, maximum,
                MonitoringFieldRuntimeSupport.SUPPORTED, legacy, categories, List.of());
    }

    private static Seed choice(
            String code, String displayName, String description, MonitoringFieldRuntimeSupport support,
            MonitoringField legacy, Set<MonitoringCategory> categories, List<Option> options) {
        return new Seed(code, displayName, description, MonitoringAnswerType.SINGLE_CHOICE, null, null, null,
                support, legacy, categories, options);
    }

    private static Option option(String code, String label, int order) {
        return new Option(code, label, order);
    }

    record Seed(
            String code,
            String displayName,
            String description,
            MonitoringAnswerType answerType,
            String unit,
            Double minimumValue,
            Double maximumValue,
            MonitoringFieldRuntimeSupport runtimeSupport,
            MonitoringField legacyField,
            Set<MonitoringCategory> categories,
            List<Option> options
    ) {
        Seed support(MonitoringFieldRuntimeSupport runtimeSupport) {
            return new Seed(code, displayName, description, answerType, unit, minimumValue, maximumValue,
                    runtimeSupport, legacyField, categories, options);
        }
    }

    record Option(String code, String displayLabel, int displayOrder) {}
}
