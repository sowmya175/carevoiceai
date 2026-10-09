package com.carevoice.service;

import com.carevoice.domain.MonitoringField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedClinicalExtractionService implements ClinicalExtractionService {
    private static final Logger log = LoggerFactory.getLogger(RuleBasedClinicalExtractionService.class);

    private static final String PAIN_NUMBER =
            "10|[0-9]|zero|one|two|three|four|five|six|seven|eight|nine|ten";
    private static final Pattern PAIN_OUT_OF_TEN = Pattern.compile(
            "\\b(" + PAIN_NUMBER + ")\\s+out\\s+of\\s+(?:10|ten)\\b");
    private static final Pattern PAIN_STATED = Pattern.compile(
            "\\bpain\\b(?:\\s+level|\\s+score)?(?:\\s+is|\\s+was|\\s*[:=])?(?:\\s+(?:about|around|approximately))?\\s+(" + PAIN_NUMBER + ")\\b");
    private static final Pattern PAIN_SCALE = Pattern.compile(
            "\\b(?:(?:on\\s+(?:a|the)\\s+)?scale\\s+(?:of|from)\\s+)?"
                    + "(?:0|1|zero|one)\\s*(?:to|-|through)\\s*(?:10|ten)\\b");
    private static final Pattern PAIN_RATING = Pattern.compile(
            "(?<!\\d\\.)\\b(" + PAIN_NUMBER + ")\\b(?!\\.\\d)");
    private static final Pattern ONSET = Pattern.compile("(?i)\\b(?:started|began|since)\\s+(.+)$");
    private static final Pattern TEMPERATURE = Pattern.compile(
            "\\b(?:temperature|temp)\\b(?:\\s+is|\\s+was)?\\s*[:=]?\\s*(\\d{2,3}(?:\\.\\d+)?)");

    @Override
    public ExtractedClinicalFacts extract(String message, MonitoringSessionContext context) {
        long started = System.nanoTime();
        try {
            return extractFacts(message, context);
        } finally {
            Long sessionId = context == null ? null : context.sessionId();
            VoiceTiming.log(log, "ruleExtractionMs=" + VoiceTiming.millisSince(started) + " sessionId=" + sessionId);
            if (!VoiceTiming.insideHybridExtraction()) {
                VoiceTiming.log(log, "geminiExtraction=SKIPPED geminiExtractionMs=0 sessionId=" + sessionId);
            }
        }
    }

    private ExtractedClinicalFacts extractFacts(String message, MonitoringSessionContext context) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT).replace('’', '\'');
        MonitoringField requested = context == null ? null : context.previouslyRequestedField();
        if (text.trim().matches("(?:okay|ok|maybe|not sure|i don't know|i do not know|unsure)[.!?]*")) {
            return ExtractedClinicalFacts.none();
        }

        return new ClinicalExtractionValidator().validate(new ExtractedClinicalFacts(
                painScore(text, requested),
                dizziness(text),
                dizzinessOnset(message, text, context, requested),
                lossOfConsciousness(text, requested),
                medicationTaken(text, requested),
                appetite(text, requested),
                sleepQuality(text, requested),
                shortnessOfBreath(text),
                temperature(text)
        ), message, context);
    }

    private Integer painScore(String text, MonitoringField requested) {
        Integer stated = statedPainScore(text);
        if (stated != null) {
            return stated;
        }
        if (requested != MonitoringField.PAIN_SCORE) {
            return null;
        }
        return directedPainScore(text);
    }

    private Integer statedPainScore(String text) {
        Matcher outOfTen = PAIN_OUT_OF_TEN.matcher(text);
        if (outOfTen.find()) {
            return toScore(outOfTen.group(1));
        }
        Matcher stated = PAIN_STATED.matcher(text);
        if (stated.find()) {
            return toScore(stated.group(1));
        }
        return null;
    }

    private Integer directedPainScore(String text) {
        String answer = PAIN_SCALE.matcher(text).replaceAll(" ");
        Matcher ratings = PAIN_RATING.matcher(answer);
        Integer score = null;
        while (ratings.find()) {
            int value = toScore(ratings.group(1));
            if (value < 0 || value > 10) {
                return null;
            }
            if (score != null && score != value) {
                return null;
            }
            score = value;
        }
        return score;
    }

    private Integer toScore(String token) {
        return switch (token) {
            case "zero" -> 0;
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            case "four" -> 4;
            case "five" -> 5;
            case "six" -> 6;
            case "seven" -> 7;
            case "eight" -> 8;
            case "nine" -> 9;
            case "ten" -> 10;
            default -> Integer.parseInt(token);
        };
    }

    private Boolean dizziness(String text) {
        if (contains(text,
                "do not feel dizzy",
                "don't feel dizzy",
                "not feeling dizzy",
                "am not dizzy",
                "i'm not dizzy",
                "not dizzy",
                "no dizziness",
                "without dizziness",
                "not lightheaded",
                "not light-headed",
                "no lightheaded")) {
            return false;
        }
        if (contains(text,
                "feel dizzy",
                "feeling dizzy",
                "been dizzy",
                "i'm dizzy",
                "am dizzy",
                "dizzy",
                "dizziness",
                "lightheaded",
                "light-headed")) {
            return true;
        }
        return null;
    }

    private String dizzinessOnset(String message, String text, MonitoringSessionContext context, MonitoringField requested) {
        if (message == null || message.isBlank()) {
            return null;
        }
        if (requested == MonitoringField.DIZZINESS_ONSET) {
            // A directed response still needs to describe a time, not just acknowledge the question.
            if (text.matches(".*\\b(today|yesterday|morning|afternoon|evening|night|ago|when|since|at|after|before|hours?|minutes?|days?|weeks?)\\b.*")) {
                return onsetPhrase(message);
            }
            return null;
        }
        boolean dizzy = Boolean.TRUE.equals(dizziness(text))
                || (context != null && Boolean.TRUE.equals(context.collectedFacts().dizziness()));
        if (!dizzy) {
            return null;
        }
        Matcher matcher = ONSET.matcher(message.trim());
        if (matcher.find()) {
            return clean(matcher.group(1));
        }
        return null;
    }

    private String onsetPhrase(String message) {
        Matcher matcher = ONSET.matcher(message.trim());
        if (matcher.find()) {
            return clean(matcher.group(1));
        }
        return clean(message);
    }

    private Boolean lossOfConsciousness(String text, MonitoringField requested) {
        if (ClinicalExtractionValidator.nearSyncope(text)) {
            return null;
        }
        Boolean directed = directedYesNo(text, requested, MonitoringField.LOSS_OF_CONSCIOUSNESS);
        if (directed != null) {
            return directed;
        }
        if (contains(text,
                "did not lose consciousness",
                "didn't lose consciousness",
                "no loss of consciousness",
                "did not faint",
                "didn't faint",
                "didn't actually faint",
                "did not actually faint",
                "never fainted",
                "haven't fainted",
                "have not fainted",
                "did not pass out",
                "didn't pass out",
                "never passed out")) {
            return false;
        }
        if (contains(text, "fainted", "passed out", "lost consciousness")) {
            return true;
        }
        return null;
    }

    private Boolean medicationTaken(String text, MonitoringField requested) {
        Boolean directed = directedYesNo(text, requested, MonitoringField.MEDICATION_TAKEN);
        if (directed != null) {
            return directed;
        }
        if (contains(text,
                "forgot to take my medicine",
                "haven't taken my medication",
                "have not taken my medication",
                "haven't taken my medicine",
                "have not taken my medicine",
                "did not take my medication",
                "didn't take my medication",
                "did not take my medicine",
                "didn't take my medicine",
                "forgot my medication",
                "forgot to take my medication",
                "forgot my medicine",
                "missed my medication",
                "missed my medicine")) {
            return false;
        }
        if (contains(text,
                "took my medication",
                "did take my medication",
                "did take my medicine",
                "took the medication",
                "took my medicine",
                "took the medicine",
                "have taken my medication",
                "i've taken my medication",
                "medication taken")) {
            return true;
        }
        if (requested == MonitoringField.MEDICATION_TAKEN) {
            if (contains(text, "did not take it", "didn't take it", "haven't taken it", "have not taken it")) {
                return false;
            }
            if (text.trim().matches("(?:i )?(?:did|have|took it|taken it|already took it)[.!?]*")) {
                return true;
            }
            if (text.trim().matches("(?:not yet|i forgot|i missed it)[.!?]*")) {
                return false;
            }
        }
        return null;
    }

    private String appetite(String text, MonitoringField requested) {
        if (contains(text, "poor appetite", "appetite is poor", "appetite has been poor", "barely ate", "ate nothing")) {
            return "poor";
        }
        if (contains(text,
                "reduced appetite",
                "appetite is reduced",
                "not eating much",
                "haven't eaten much",
                "have not eaten much")) {
            return "reduced";
        }
        if (contains(text, "haven't really felt hungry", "have not really felt hungry",
                "haven't felt hungry", "have not felt hungry",
                "not hungry", "less hungry", "less than usual")) {
            return "reduced";
        }
        if (contains(text, "good appetite", "appetite is good", "eating well")) {
            return "good";
        }
        if (contains(text, "appetite is normal", "normal appetite", "eating normally")) {
            return "normal";
        }
        if (requested == MonitoringField.APPETITE) {
            if (contains(text, "not good", "n't good")) {
                return "poor";
            }
            return firstWord(text, "poor", "reduced", "normal", "good");
        }
        return null;
    }

    private String sleepQuality(String text, MonitoringField requested) {
        if (contains(text,
                "didn't sleep well",
                "did not sleep well",
                "haven't slept well",
                "slept badly",
                "slept bad",
                "barely slept",
                "poor sleep",
                "bad sleep")) {
            return "poor";
        }
        if (contains(text, "keep waking up", "kept waking up", "waking up in the middle of the night",
                "can't sleep", "cannot sleep", "couldn't sleep", "trouble sleeping")) {
            return "poor";
        }
        if (contains(text, "sleep was normal", "slept normally", "normal sleep")) {
            return "normal";
        }
        if (contains(text, "slept really well", "slept well", "slept fine", "good sleep")) {
            return "good";
        }
        if (requested == MonitoringField.SLEEP_QUALITY) {
            if (contains(text, "not well", "not good", "not fine", "badly", "poor")) {
                return "poor";
            }
            if (text.trim().matches("(?:well|fine|really well|pretty well)[.!?]*")) {
                return "good";
            }
            return firstWord(text, "normal", "good", "poor");
        }
        return null;
    }

    private Boolean shortnessOfBreath(String text) {
        if (contains(text,
                "no shortness of breath",
                "not short of breath",
                "no difficulty breathing",
                "don't have any trouble breathing",
                "do not have any trouble breathing",
                "don't have trouble breathing",
                "do not have trouble breathing",
                "no trouble breathing",
                "breathing normally")) {
            return false;
        }
        if (contains(text,
                "shortness of breath",
                "short of breath",
                "difficulty breathing",
                "trouble breathing",
                "can't breathe",
                "cannot breathe")) {
            return true;
        }
        return null;
    }

    private Double temperature(String text) {
        Matcher matcher = TEMPERATURE.matcher(text);
        if (matcher.find()) {
            return Double.valueOf(matcher.group(1));
        }
        return null;
    }

    private String firstWord(String text, String... words) {
        for (String word : words) {
            if (text.trim().matches("(?:it's |it is |pretty |quite |really )?" + word + "(?: today)?[.!?]*")) {
                return word;
            }
        }
        return null;
    }

    private boolean contains(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private Boolean directedYesNo(String text, MonitoringField requested, MonitoringField field) {
        if (requested != field || text == null) {
            return null;
        }
        if (text.matches("(?i).*\\b(maybe|unsure|not sure|don't know|do not know|but)\\b.*")) {
            return null;
        }
        if (text.matches("(?i)\\s*(yes|yeah|yep|yup)\\b.*")
                && !text.matches(".*\\b(no|not|never|haven't|didn't)\\b.*")) {
            return true;
        }
        if (text.matches("(?i)\\s*(no|nope|nah)\\b.*")) {
            return false;
        }
        return null;
    }

    private String clean(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
