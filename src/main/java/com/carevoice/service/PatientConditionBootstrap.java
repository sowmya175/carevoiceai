package com.carevoice.service;
import com.carevoice.domain.PatientCondition;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Copies a legacy free-text condition into one primary PatientCondition.
 * The category is always OTHER. Repeated startup does not add another row.
 */
@Component
@Order(7)
public class PatientConditionBootstrap implements ApplicationRunner {
    private final PatientConditionService conditions;

    public PatientConditionBootstrap(PatientConditionService conditions) {
        this.conditions = conditions;
    }

    @Override
    public void run(ApplicationArguments args) {
        conditions.bootstrapMissing();
    }
}
