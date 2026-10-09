package com.carevoice.service;

import com.carevoice.domain.DailyCheckInStatus;
import com.carevoice.dto.monitoring.DailyCheckInStatusResponse;
import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.MonitoringTurn;
import com.carevoice.domain.Patient;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;

import com.carevoice.domain.PatientReminderPreference;
import com.carevoice.repository.PatientReminderPreferenceRepository;
import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.repository.PatientRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicianReviewService {
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final PatientRepository patients;
    private final DailyCheckInService dailyCheckIns;
    private final MonitoringPlanService monitoringPlans;
    private final PatientReminderPreferenceRepository preferences;
    private final MonitoringSessionRepository sessions;
    private final MonitoringTurnRepository turns;
    private final ClinicalNoteRepository notes;

    public ClinicianReviewService(
            PatientRepository patients,
            DailyCheckInService dailyCheckIns,
            MonitoringPlanService monitoringPlans,
            PatientReminderPreferenceRepository preferences,
            MonitoringSessionRepository sessions,
            MonitoringTurnRepository turns,
            ClinicalNoteRepository notes) {
        this.patients = patients;
        this.dailyCheckIns = dailyCheckIns;
        this.monitoringPlans = monitoringPlans;
        this.preferences = preferences;
        this.sessions = sessions;
        this.turns = turns;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public ClinicianPatientOverview patient(Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
        DailyCheckInStatusResponse today = dailyCheckIns.status(patientId);
        MonitoringSession session = today.sessionId() == null
                ? null
                : sessions.findById(today.sessionId()).orElse(null);
        PatientReminderPreference preference = preferences.findByPatient_Id(patientId).orElse(null);
        int answered = session == null ? 0 : turns.findByMonitoringSession_IdOrderBySequenceNumberAsc(session.getId()).size();
        return new ClinicianPatientOverview(
                patient.getId(),
                patient.getFullName(),
                patient.getMedicalCondition(),
                patient.getTimezone(),
                monitoringPlans.activePlanName(patientId),
                preference != null && preference.isEnabled(),
                preference == null ? null : preference.getReminderTime().format(HOUR_MINUTE),
                today.checkInDate(),
                today.currentDate(),
                today.status(),
                today.sessionId(),
                session == null ? null : session.getMonitoringPlanName(),
                session == null ? null : session.getCreatedAt(),
                session == null ? null : session.getCompletedAt(),
                session == null ? null : session.getRiskLevel(),
                session == null ? null : session.getEscalationReason(),
                answered,
                today.previousDaySession(),
                today.status() == DailyCheckInStatus.READY_FOR_REVIEW);
    }

    @Transactional(readOnly = true)
    public ClinicianSessionDetail session(Long sessionId) {
        MonitoringSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring session not found."));
        Map<Long, ClinicalNote> notesByTurn = new HashMap<>();
        for (ClinicalNote note : notes.findByMonitoringTurn_MonitoringSession_Id(sessionId)) {
            notesByTurn.put(note.getMonitoringTurn().getId(), note);
        }
        List<ClinicianTurnDetail> history = turns.findByMonitoringSession_IdOrderBySequenceNumberAsc(sessionId)
                .stream()
                .map(turn -> toTurn(turn, notesByTurn.get(turn.getId())))
                .toList();
        return new ClinicianSessionDetail(
                session.getId(),
                session.getPatient().getId(),
                session.getCheckInDate(),
                session.getMonitoringPlanName(),
                session.getStatus(),
                session.getRiskLevel(),
                session.getEscalationReason(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                ClinicianFacts.fromSession(session),
                history);
    }

    private static ClinicianTurnDetail toTurn(MonitoringTurn turn, ClinicalNote note) {
        ClinicianNoteView view = note == null ? null : new ClinicianNoteView(
                note.getNoteText(),
                noteLabel(note.getNoteProvider()),
                note.getRiskLevel(),
                note.getEscalationReason(),
                ClinicianFacts.fromTurn(note.getExtractedFactsJson()));
        return new ClinicianTurnDetail(
                turn.getSequenceNumber(),
                turn.getQuestion(),
                turn.getPatientResponse(),
                turn.getInputMode(),
                turn.getCreatedAt(),
                view);
    }

    static String noteLabel(String provider) {
        if (provider != null && !provider.isBlank() && !"deterministic".equals(provider)) {
            return "AI-assisted CareVoice Note";
        }
        return "CareVoice Note";
    }

    public record ClinicianPatientOverview(
            Long patientId,
            String fullName,
            String medicalCondition,
            String timezone,
            String activeMonitoringPlanName,
            boolean reminderEnabled,
            String reminderTime,
            LocalDate checkInDate,
            LocalDate currentDate,
            DailyCheckInStatus todayStatus,
            Long todaySessionId,
            String sessionPlanName,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            RiskLevel monitoringFlag,
            String escalationReason,
            int questionsAnswered,
            boolean previousDaySession,
            boolean requiresReview
    ) {}

    public record ClinicianSessionDetail(
            Long sessionId,
            Long patientId,
            LocalDate checkInDate,
            String monitoringPlanName,
            SessionStatus status,
            RiskLevel monitoringFlag,
            String escalationReason,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            List<ClinicianFacts.ClinicianFact> sessionFacts,
            List<ClinicianTurnDetail> turns
    ) {}

    public record ClinicianTurnDetail(
            int sequence,
            String question,
            String patientResponse,
            com.carevoice.domain.InputMode inputMode,
            Instant createdAt,
            ClinicianNoteView clinicalNote
    ) {}

    public record ClinicianNoteView(
            String noteText,
            String noteLabel,
            RiskLevel monitoringFlag,
            String escalationReason,
            List<ClinicianFacts.ClinicianFact> facts
    ) {}
}
