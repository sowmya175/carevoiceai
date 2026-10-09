package com.carevoice.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rule-based wording screen for daily questions.
 * It is not a clinical-safety validator. Exact wording match is not required.
 */
public final class PlanWordingChecker {
    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 200;
    private static final List<Pattern> PROHIBITED = List.of(
            Pattern.compile("\\bdiagnos"),
            Pattern.compile("\\byou should\\b"),
            Pattern.compile("\\bi recommend\\b"),
            Pattern.compile("change your"),
            Pattern.compile("stop taking"),
            Pattern.compile("increase your"),
            Pattern.compile("decrease your"),
            Pattern.compile("\\bdose\\b"),
            Pattern.compile("\\bemergency\\b"),
            Pattern.compile("call 911"),
            Pattern.compile("\\bcomplication"),
            Pattern.compile("\\bdangerous\\b"),
            Pattern.compile("\\bworsening\\b"),
            Pattern.compile("treatment plan"),
            Pattern.compile("new medication"));
    private static final Map<String, String> SEMANTIC_TOKEN = Map.of(
            "PAIN_SCORE", "pain",
            "MEDICATION_TAKEN", "medication",
            "APPETITE", "appetite",
            "SLEEP_QUALITY", "sleep",
            "TEMPERATURE", "temperature");
    private static final List<String> UNSUPPORTED_CONCEPTS = List.of(
            "incision", "blood pressure", "glucose", "oxygen", "swelling", "mobility");

    private PlanWordingChecker() {}

    public static List<String> findings(String exampleId, String fieldCode, String questionText) {
        List<String> findings = new ArrayList<>();
        String text = questionText == null ? "" : questionText.trim();
        if (text.isBlank()) {
            findings.add(exampleId + ": blank question text");
            return findings;
        }
        if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) {
            findings.add(exampleId + ": question length is outside the expected range");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Pattern pattern : PROHIBITED) {
            if (pattern.matcher(lower).find()) {
                findings.add(exampleId + ": question wording matches a prohibited pattern");
                break;
            }
        }
        for (String concept : UNSUPPORTED_CONCEPTS) {
            if (lower.contains(concept)) {
                findings.add(exampleId + ": question wording mentions an unsupported concept");
                break;
            }
        }
        String token = SEMANTIC_TOKEN.get(fieldCode);
        if (token != null && !lower.contains(token)) {
            findings.add(exampleId + ": question wording does not mention the field");
        }
        return findings;
    }
}
