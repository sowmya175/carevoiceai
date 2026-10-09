package com.carevoice.checkin;

import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.SessionStatus;
import com.carevoice.plan.MonitoringPlanService;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.PatientMonitoringPlanRepository;
import com.carevoice.repository.PatientRepository;
import com.carevoice.service.MonitoringAgentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DailyCheckInService {
    private final PatientRepository patients;
    private final MonitoringSessionRepository sessions;
    private final PatientMonitoringPlanRepository assignments;
    private final MonitoringPlanService monitoringPlans;
    private final PatientLocalDateService dates;
    private final PlatformTransactionManager transactions;

    public DailyCheckInService(
            PatientRepository patients,
            MonitoringSessionRepository sessions,
            PatientMonitoringPlanRepository assignments,
            MonitoringPlanService monitoringPlans,
            PatientLocalDateService dates,
            PlatformTransactionManager transactions) {
        this.patients = patients;
        this.sessions = sessions;
        this.assignments = assignments;
        this.monitoringPlans = monitoringPlans;
        this.dates = dates;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public DailyCheckInStatusResponse status(Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
        LocalDate today = dates.today(patient);
        String timezone = dates.zone(patient).getId();
        Optional<MonitoringSession> earlier = unfinishedBefore(patientId, today);
        if (earlier.isPresent()) {
            return fromSession(earlier.get(), today, timezone, true);
        }
        return sessions.findByPatient_IdAndCheckInDate(patientId, today)
                .map(session -> fromSession(session, today, timezone, false))
                .orElseGet(() -> new DailyCheckInStatusResponse(
                        today,
                        today,
                        timezone,
                        DailyCheckInStatus.NOT_STARTED,
                        null,
                        monitoringPlans.activePlanName(patientId),
                        null,
                        null,
                        false));
    }

    /**
     * Returns today's session, or an older unfinished session that must be finished first.
     * A second request for the same patient-local date returns the original session.
     */
    public MonitoringSession startToday(Long patientId) {
        try {
            MonitoringSession session = insertOrContinue(patientId);
            if (session == null) {
                throw new IllegalArgumentException("Patient not found.");
            }
            return session;
        } catch (DataIntegrityViolationException ex) {
            return existingAfterConflict(patientId);
        }
    }

    @Transactional(readOnly = true)
    public List<ClinicianPatientDaily> clinicianPatients() {
        List<Patient> all = patients.findAll().stream()
                .sorted(Comparator.comparing(Patient::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (all.isEmpty()) {
            return List.of();
        }
        List<Long> ids = all.stream().map(Patient::getId).toList();
        Map<Long, LocalDate> todayByPatient = new HashMap<>();
        Set<LocalDate> dates = new HashSet<>();
        for (Patient patient : all) {
            LocalDate today = this.dates.today(patient);
            todayByPatient.put(patient.getId(), today);
            dates.add(today);
        }
        Map<Long, String> planNames = new HashMap<>();
        for (Object[] row : assignments.findActivePlanNames(ids)) {
            planNames.put((Long) row[0], (String) row[1]);
        }
        List<DailySessionRow> rows = sessions.findForDailyStatus(ids, SessionStatus.IN_PROGRESS, dates);
        return all.stream()
                .map(patient -> clinicianRow(patient, todayByPatient.get(patient.getId()), planNames.get(patient.getId()), rows))
                .toList();
    }

    private MonitoringSession insertOrContinue(Long patientId) {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> {
            Patient patient = patients.findByIdForUpdate(patientId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
            LocalDate today = dates.today(patient);
            Optional<MonitoringSession> earlier = unfinishedBefore(patientId, today);
            if (earlier.isPresent()) {
                return earlier.get();
            }
            Optional<MonitoringSession> existing = sessions.findByPatient_IdAndCheckInDate(patientId, today);
            if (existing.isPresent()) {
                return existing.get();
            }
            MonitoringSession session = new MonitoringSession(patient);
            session.setCheckInDate(today);
            session.setNextQuestion(MonitoringAgentService.OPENING_QUESTION);
            monitoringPlans.capture(session);
            return sessions.saveAndFlush(session);
        });
    }

    private MonitoringSession existingAfterConflict(Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
        LocalDate today = dates.today(patient);
        return sessions.findByPatient_IdAndCheckInDate(patientId, today)
                .or(() -> unfinishedBefore(patientId, today))
                .orElseThrow(() -> new IllegalArgumentException("Today's check-in could not be opened."));
    }

    private Optional<MonitoringSession> unfinishedBefore(Long patientId, LocalDate today) {
        return sessions.findFirstByPatient_IdAndStatusAndCheckInDateLessThanOrderByCheckInDateAscIdAsc(
                patientId, SessionStatus.IN_PROGRESS, today);
    }

    private ClinicianPatientDaily clinicianRow(
            Patient patient, LocalDate today, String assignedPlan, List<DailySessionRow> rows) {
        DailySessionRow earlier = rows.stream()
                .filter(row -> patient.getId().equals(row.patientId()))
                .filter(row -> row.status() == SessionStatus.IN_PROGRESS && row.checkInDate().isBefore(today))
                .min(Comparator.comparing(DailySessionRow::checkInDate).thenComparing(DailySessionRow::sessionId))
                .orElse(null);
        if (earlier != null) {
            return summary(patient, earlier.checkInDate(), DailyCheckInStatus.IN_PROGRESS, earlier, assignedPlan);
        }
        DailySessionRow todayRow = rows.stream()
                .filter(row -> patient.getId().equals(row.patientId()) && today.equals(row.checkInDate()))
                .findFirst()
                .orElse(null);
        if (todayRow == null) {
            return summary(patient, today, DailyCheckInStatus.NOT_STARTED, null, assignedPlan);
        }
        return summary(patient, today, from(todayRow.status()), todayRow, assignedPlan);
    }

    private static String planName(String snapshot, String assigned) {
        return snapshot == null || snapshot.isBlank() ? assigned : snapshot;
    }

    private static ClinicianPatientDaily summary(
            Patient patient, LocalDate date, DailyCheckInStatus status, DailySessionRow session, String assignedPlan) {
        String plan = session == null ? assignedPlan : planName(session.monitoringPlanName(), assignedPlan);
        OffsetDateTime latest = session == null ? null
                : session.completedAt() != null ? session.completedAt() : session.startedAt();
        return new ClinicianPatientDaily(
                patient.getId(),
                patient.getFullName(),
                patient.getMedicalCondition(),
                patient.getTimezone(),
                plan,
                date,
                status,
                session == null ? null : session.sessionId(),
                session == null ? null : session.riskLevel(),
                latest,
                status == DailyCheckInStatus.READY_FOR_REVIEW);
    }

    private static DailyCheckInStatusResponse fromSession(
            MonitoringSession session, LocalDate today, String timezone, boolean previousDay) {
        return new DailyCheckInStatusResponse(
                session.getCheckInDate(),
                today,
                timezone,
                from(session.getStatus()),
                session.getId(),
                session.getMonitoringPlanName(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                previousDay);
    }

    private static DailyCheckInStatus from(SessionStatus status) {
        return switch (status) {
            case IN_PROGRESS -> DailyCheckInStatus.IN_PROGRESS;
            case COMPLETED -> DailyCheckInStatus.COMPLETED;
            case READY_FOR_REVIEW -> DailyCheckInStatus.READY_FOR_REVIEW;
        };
    }
}
