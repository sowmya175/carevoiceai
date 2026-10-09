package com.carevoice.service;
import com.carevoice.exception.MonitoringPlanUnavailableException;
import com.carevoice.domain.PlanField;

import com.carevoice.domain.UserAccount;
import com.carevoice.domain.MonitoringPlan;
import com.carevoice.domain.MonitoringPlanQuestion;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.domain.PatientMonitoringPlan;
import com.carevoice.repository.MonitoringPlanQuestionRepository;
import com.carevoice.repository.MonitoringPlanRepository;
import com.carevoice.repository.PatientMonitoringPlanRepository;
import com.carevoice.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class MonitoringPlanService {
    private final MonitoringPlanRepository plans;
    private final MonitoringPlanQuestionRepository questions;
    private final PatientMonitoringPlanRepository assignments;
    private final PatientRepository patients;

    public MonitoringPlanService(
            MonitoringPlanRepository plans,
            MonitoringPlanQuestionRepository questions,
            PatientMonitoringPlanRepository assignments,
            PatientRepository patients) {
        this.plans = plans;
        this.questions = questions;
        this.assignments = assignments;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> availablePlans() {
        return plans.findByActiveTrueAndOwnerPatientIdIsNullOrderByNameAsc().stream().map(PlanSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public String activePlanName(Long patientId) {
        return active(patientId).map(assignment -> assignment.getMonitoringPlan().getName()).orElse(null);
    }

    @Transactional
    public AssignmentView activeAssignment(Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
        PatientMonitoringPlan assignment = assignGeneralIfMissing(patient);
        return AssignmentView.from(assignment);
    }

    @Transactional
    public PatientPlanView patientPlan(Long patientId) {
        AssignmentView assignment = activeAssignment(patientId);
        return new PatientPlanView(assignment.name(), assignment.description());
    }

    @Transactional
    public AssignmentView assign(Long patientId, Long monitoringPlanId, UserAccount assignedBy) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found."));
        MonitoringPlan plan = plans.findById(monitoringPlanId)
                .filter(MonitoringPlan::isActive)
                .filter(candidate -> candidate.getOwnerPatientId() == null
                        || candidate.getOwnerPatientId().equals(patientId))
                .orElseThrow(() -> new IllegalArgumentException("That monitoring plan is not available."));
        return AssignmentView.from(replace(patient, plan, assignedBy));
    }

    @Transactional
    public PatientMonitoringPlan assignGeneralIfMissing(Patient patient) {
        return active(patient.getId()).orElseGet(() -> {
            MonitoringPlan general = plans.findByCode(DemoMonitoringPlans.GENERAL)
                    .filter(MonitoringPlan::isActive)
                    .orElseThrow(MonitoringPlanUnavailableException::new);
            return replace(patient, general, null);
        });
    }

    @Transactional
    public void capture(MonitoringSession session) {
        PatientMonitoringPlan assignment = assignGeneralIfMissing(session.getPatient());
        MonitoringPlan plan = assignment.getMonitoringPlan();
        List<PlanField> fields = questions.findByMonitoringPlan_IdAndActiveTrueOrderByDisplayOrderAsc(plan.getId())
                .stream()
                .map(MonitoringPlanQuestion::toPlanField)
                .filter(PlanField::required)
                .toList();
        if (fields.isEmpty()) {
            throw new MonitoringPlanUnavailableException();
        }
        session.capturePlan(plan, fields);
    }

    private java.util.Optional<PatientMonitoringPlan> active(Long patientId) {
        List<PatientMonitoringPlan> current = assignments.findByPatient_IdAndActiveTrue(patientId);
        if (current.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(current.get(0));
    }

    private PatientMonitoringPlan replace(Patient patient, MonitoringPlan plan, UserAccount assignedBy) {
        List<PatientMonitoringPlan> current = assignments.findByPatient_IdAndActiveTrue(patient.getId());
        for (PatientMonitoringPlan existing : current) {
            existing.end();
        }
        if (!current.isEmpty()) {
            assignments.saveAll(current);
            assignments.flush();
        }
        return assignments.save(new PatientMonitoringPlan(patient, plan, assignedBy));
    }

    public record PlanSummary(Long id, String name, String description, String conditionLabel) {
        static PlanSummary from(MonitoringPlan plan) {
            return new PlanSummary(plan.getId(), plan.getName(), plan.getDescription(), plan.getConditionLabel());
        }
    }

    public record AssignmentView(
            Long monitoringPlanId,
            String name,
            String description,
            String conditionLabel,
            Instant assignedAt
    ) {
        static AssignmentView from(PatientMonitoringPlan assignment) {
            MonitoringPlan plan = assignment.getMonitoringPlan();
            return new AssignmentView(
                    plan.getId(),
                    plan.getName(),
                    plan.getDescription(),
                    plan.getConditionLabel(),
                    assignment.getAssignedAt());
        }
    }

    public record PatientPlanView(String name, String description) {}
}
