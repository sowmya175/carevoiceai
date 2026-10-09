package com.carevoice.condition;

import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.Patient;
import com.carevoice.domain.PatientCondition;
import com.carevoice.repository.PatientConditionRepository;
import com.carevoice.repository.PatientRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Structured conditions are the source of truth.
 * {@code Patient.medicalCondition} is kept equal to the active primary condition name
 * so existing screens keep working. Conditions do not change the assigned monitoring plan.
 */
@Service
public class PatientConditionService {
    private final PatientRepository patients;
    private final PatientConditionRepository conditions;

    public PatientConditionService(PatientRepository patients, PatientConditionRepository conditions) {
        this.patients = patients;
        this.conditions = conditions;
    }

    @Transactional
    public void bootstrapMissing() {
        for (Patient patient : patients.findAll()) {
            ensureInitial(patient.getId());
        }
    }

    @Transactional
    public void ensureInitial(Long patientId) {
        if (patientId == null) {
            return;
        }
        Patient patient = patients.findByIdForUpdate(patientId).orElse(null);
        if (patient == null || conditions.existsByPatient_Id(patientId)) {
            return;
        }
        String name = normalize(patient.getMedicalCondition());
        if (name.isEmpty()) {
            return;
        }
        conditions.save(new PatientCondition(patient, name, MonitoringCategory.OTHER, true, true));
    }

    @Transactional(readOnly = true)
    public List<ClinicianCondition> listForClinician(Long patientId) {
        requirePatient(patientId);
        return conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId).stream()
                .sorted(displayOrder())
                .map(ClinicianCondition::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PatientConditionSummary> listActiveForPatient(Long patientId) {
        requirePatient(patientId);
        return conditions.findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(patientId).stream()
                .filter(PatientCondition::isActive)
                .sorted(displayOrder())
                .map(row -> new PatientConditionSummary(row.getConditionName(), row.getMonitoringCategory(), row.isPrimaryCondition()))
                .toList();
    }

    @Transactional
    public ClinicianCondition create(Long patientId, String conditionName, MonitoringCategory category, boolean primary) {
        Patient patient = lockedPatient(patientId);
        List<PatientCondition> rows = conditions.lockByPatientId(patientId);
        String name = requiredName(conditionName);
        requireCategory(category);
        rejectDuplicate(rows, name, null);
        boolean makePrimary = primary || rows.stream().noneMatch(PatientCondition::isActive);
        if (makePrimary) {
            clearPrimary(rows);
        }
        PatientCondition created = conditions.save(new PatientCondition(patient, name, category, true, makePrimary));
        if (makePrimary) {
            patient.setMedicalCondition(name);
        }
        return ClinicianCondition.from(created);
    }

    @Transactional
    public ClinicianCondition update(
            Long patientId, Long conditionId, String conditionName, MonitoringCategory category,
            boolean active, boolean primary) {
        Patient patient = lockedPatient(patientId);
        List<PatientCondition> rows = conditions.lockByPatientId(patientId);
        PatientCondition row = rows.stream()
                .filter(candidate -> candidate.getId().equals(conditionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Condition not found."));
        String name = requiredName(conditionName);
        requireCategory(category);
        if (primary && !active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An inactive condition cannot be the primary condition.");
        }
        if (!active) {
            deactivate(rows, row, name, category);
            return ClinicianCondition.from(row);
        }
        rejectDuplicate(rows, name, row.getId());
        row.rename(name);
        row.categorize(category);
        row.setActive(true);
        if (primary) {
            clearPrimary(rows);
            row.setPrimaryCondition(true);
            patient.setMedicalCondition(name);
        } else if (row.isPrimaryCondition()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Choose another condition as the primary condition.");
        } else if (rows.stream().noneMatch(PatientCondition::isPrimaryCondition)) {
            row.setPrimaryCondition(true);
            patient.setMedicalCondition(name);
        }
        return ClinicianCondition.from(row);
    }

    private void deactivate(List<PatientCondition> rows, PatientCondition row, String name, MonitoringCategory category) {
        boolean othersActive = rows.stream().anyMatch(candidate -> candidate.isActive() && !candidate.getId().equals(row.getId()));
        if (!othersActive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Keep at least one active condition.");
        }
        if (row.isPrimaryCondition()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Set another active condition as primary before deactivating this one.");
        }
        row.rename(name);
        row.categorize(category);
        row.setActive(false);
        row.setPrimaryCondition(false);
    }

    private Patient lockedPatient(Long patientId) {
        return patients.findByIdForUpdate(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
    }

    private void requirePatient(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new IllegalArgumentException("Patient not found.");
        }
    }

    private static void clearPrimary(List<PatientCondition> rows) {
        for (PatientCondition row : rows) {
            row.setPrimaryCondition(false);
        }
    }

    private static void rejectDuplicate(List<PatientCondition> rows, String name, Long exceptId) {
        String key = key(name);
        boolean duplicate = rows.stream()
                .filter(PatientCondition::isActive)
                .filter(row -> exceptId == null || !exceptId.equals(row.getId()))
                .anyMatch(row -> key(row.getConditionName()).equals(key));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That condition is already active for this patient.");
        }
    }

    private static String requiredName(String conditionName) {
        String name = normalize(conditionName);
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a condition or procedure.");
        }
        if (name.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Condition name must be 255 characters or fewer.");
        }
        return name;
    }

    private static void requireCategory(MonitoringCategory category) {
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a monitoring category.");
        }
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String key(String name) {
        return normalize(name).toLowerCase(Locale.ROOT);
    }

    private static Comparator<PatientCondition> displayOrder() {
        return Comparator.comparing(PatientCondition::isPrimaryCondition).reversed()
                .thenComparing(Comparator.comparing(PatientCondition::isActive).reversed())
                .thenComparing(row -> row.getConditionName().toLowerCase(Locale.ROOT));
    }

    public record ClinicianCondition(
            Long id,
            String conditionName,
            MonitoringCategory monitoringCategory,
            boolean active,
            boolean primaryCondition,
            Instant createdAt,
            Instant updatedAt
    ) {
        static ClinicianCondition from(PatientCondition row) {
            return new ClinicianCondition(
                    row.getId(), row.getConditionName(), row.getMonitoringCategory(),
                    row.isActive(), row.isPrimaryCondition(), row.getCreatedAt(), row.getUpdatedAt());
        }
    }

    public record PatientConditionSummary(
            String conditionName,
            MonitoringCategory monitoringCategory,
            boolean primary
    ) {}
}
