package com.carevoice.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Development guard for canonical training files.
 * This is not a medical-grade de-identification system and does not replace formal privacy review.
 */
public final class PlanPhiScanner {
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "patientname", "patient_name", "username", "email", "phone", "telephone",
            "dateofbirth", "date_of_birth", "dob", "address", "patientid", "patient_id",
            "password", "transcript", "clinicalnote", "clinical_note", "mrn", "ssn",
            "medicalrecordnumber");
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");

    private PlanPhiScanner() {}

    public static List<String> findings(ObjectMapper mapper, PlanExample example) {
        return findings(example.id(), mapper.valueToTree(example));
    }

    public static List<String> findings(String id, JsonNode node) {
        List<String> findings = new ArrayList<>();
        walk(id, node, findings);
        return findings;
    }

    private static void walk(String id, JsonNode node, List<String> findings) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name -> {
                if (FORBIDDEN_KEYS.contains(name.toLowerCase(Locale.ROOT))) {
                    findings.add(id + ": forbidden field " + name);
                }
                walk(id, node.get(name), findings);
            });
        } else if (node.isArray()) {
            node.forEach(child -> walk(id, child, findings));
        } else if (node.isTextual() && EMAIL.matcher(node.asText().toUpperCase(Locale.ROOT)).find()) {
            findings.add(id + ": text resembles an email address");
        }
    }
}
