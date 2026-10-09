package com.carevoice.plan;

import com.carevoice.domain.MonitoringField;
import com.carevoice.service.QuestionClarification;

import java.util.List;

/**
 * Demo monitoring-plan templates. They are not clinically validated protocols.
 * Gemini does not create these plans or choose which fields to collect.
 * A patient's free-text medical condition does not select a plan.
 */
public final class DemoMonitoringPlans {
    public static final String GENERAL = "GENERAL_DAILY_WELLNESS";
    public static final String POST_OPERATIVE = "POST_OPERATIVE_RECOVERY_DEMO";
    public static final String HYPERTENSION = "HYPERTENSION_SYMPTOM_MONITORING_DEMO";
    public static final String DIABETES = "DIABETES_DAILY_WELLNESS_DEMO";

    public static final String NOT_VALIDATED = "Demo monitoring plan. Not clinically validated.";

    private DemoMonitoringPlans() {}

    public static List<Template> templates() {
        return List.of(general(), postOperative(), hypertension(), diabetes());
    }

    public static Template general() {
        return new Template(
                GENERAL,
                "General Daily Wellness",
                NOT_VALIDATED + " A short daily check-in for pain, medication, appetite, and sleep.",
                "Daily wellness",
                List.of(
                        question(MonitoringField.PAIN_SCORE, 1, defaultQuestion(MonitoringField.PAIN_SCORE)),
                        question(MonitoringField.MEDICATION_TAKEN, 2, defaultQuestion(MonitoringField.MEDICATION_TAKEN)),
                        question(MonitoringField.APPETITE, 3, defaultQuestion(MonitoringField.APPETITE)),
                        question(MonitoringField.SLEEP_QUALITY, 4, defaultQuestion(MonitoringField.SLEEP_QUALITY))));
    }

    public static Template postOperative() {
        return new Template(
                POST_OPERATIVE,
                "Post-Operative Recovery Demo",
                NOT_VALIDATED + " A recovery check-in using questions CareVoice already supports. It is not a medical protocol.",
                "Post-operative recovery",
                List.of(
                        question(MonitoringField.PAIN_SCORE, 1,
                                "How would you rate your pain related to your recovery today?"),
                        question(MonitoringField.MEDICATION_TAKEN, 2, defaultQuestion(MonitoringField.MEDICATION_TAKEN)),
                        question(MonitoringField.TEMPERATURE, 3, defaultQuestion(MonitoringField.TEMPERATURE)),
                        question(MonitoringField.APPETITE, 4, defaultQuestion(MonitoringField.APPETITE)),
                        question(MonitoringField.SLEEP_QUALITY, 5, defaultQuestion(MonitoringField.SLEEP_QUALITY))));
    }

    public static Template hypertension() {
        return new Template(
                HYPERTENSION,
                "Hypertension Symptom Monitoring Demo",
                NOT_VALIDATED + " This does not replace blood-pressure measurement and does not apply blood-pressure thresholds.",
                "Hypertension symptoms",
                List.of(
                        question(MonitoringField.MEDICATION_TAKEN, 1, defaultQuestion(MonitoringField.MEDICATION_TAKEN)),
                        question(MonitoringField.SLEEP_QUALITY, 2, defaultQuestion(MonitoringField.SLEEP_QUALITY))));
    }

    public static Template diabetes() {
        return new Template(
                DIABETES,
                "Diabetes Daily Wellness Demo",
                NOT_VALIDATED + " This does not replace glucose monitoring and does not apply glucose thresholds.",
                "Diabetes daily wellness",
                List.of(
                        question(MonitoringField.MEDICATION_TAKEN, 1, defaultQuestion(MonitoringField.MEDICATION_TAKEN)),
                        question(MonitoringField.APPETITE, 2, defaultQuestion(MonitoringField.APPETITE)),
                        question(MonitoringField.SLEEP_QUALITY, 3, defaultQuestion(MonitoringField.SLEEP_QUALITY))));
    }

    public static List<PlanField> questions(String code) {
        return templates().stream()
                .filter(template -> template.code().equals(code))
                .findFirst()
                .orElseThrow()
                .questions();
    }

    /**
     * Wording used when a follow-up is required by the safety workflow but is not a routine plan question.
     */
    public static String defaultQuestion(MonitoringField field) {
        return switch (field) {
            case PAIN_SCORE -> "On a scale from 0 to 10, how would you rate your pain today?";
            case MEDICATION_TAKEN -> "Have you taken your prescribed medication today?";
            case APPETITE -> "How has your appetite been today?";
            case SLEEP_QUALITY -> "How did you sleep last night?";
            case DIZZINESS_ONSET -> "When did the dizziness start?";
            case LOSS_OF_CONSCIOUSNESS -> "Did you faint or lose consciousness?";
            case TEMPERATURE -> "What is your temperature reading?";
        };
    }

    private static PlanField question(MonitoringField field, int displayOrder, String template) {
        return new PlanField(field, template, QuestionClarification.question(field), displayOrder, true);
    }

    public record Template(
            String code,
            String name,
            String description,
            String conditionLabel,
            List<PlanField> questions
    ) {}
}
