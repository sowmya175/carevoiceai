package com.carevoice.service;
import com.carevoice.domain.PlanField;

import com.carevoice.domain.MonitoringPlan;
import com.carevoice.domain.MonitoringPlanQuestion;
import com.carevoice.domain.Patient;
import com.carevoice.repository.MonitoringPlanQuestionRepository;
import com.carevoice.repository.MonitoringPlanRepository;
import com.carevoice.repository.PatientRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts the demo plan catalog. Existing plan rows are left unchanged.
 * Patients who have no active assignment receive General Daily Wellness.
 * The patient's medical condition text is not consulted.
 */
@Component
@Order(5)
public class MonitoringPlanSeeder implements ApplicationRunner {
    private final MonitoringPlanRepository plans;
    private final MonitoringPlanQuestionRepository questions;
    private final PatientRepository patients;
    private final MonitoringPlanService assignments;

    public MonitoringPlanSeeder(
            MonitoringPlanRepository plans,
            MonitoringPlanQuestionRepository questions,
            PatientRepository patients,
            MonitoringPlanService assignments) {
        this.plans = plans;
        this.questions = questions;
        this.patients = patients;
        this.assignments = assignments;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (DemoMonitoringPlans.Template template : DemoMonitoringPlans.templates()) {
            MonitoringPlan plan = plans.findByCode(template.code()).orElseGet(() -> plans.save(
                    new MonitoringPlan(template.code(), template.name(), template.description(), template.conditionLabel())));
            if (questions.countByMonitoringPlan_Id(plan.getId()) == 0) {
                for (PlanField field : template.questions()) {
                    questions.save(new MonitoringPlanQuestion(plan, field));
                }
            }
        }
        for (Patient patient : patients.findAll()) {
            assignments.assignGeneralIfMissing(patient);
        }
    }
}
