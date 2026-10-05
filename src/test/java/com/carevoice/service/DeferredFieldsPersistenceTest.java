package com.carevoice.service;

import com.carevoice.domain.MonitoringField;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeferredFieldsPersistenceTest {
    @Test
    void multipleDeferralsAndTheActiveClarificationSurviveReload() {
        try (var factory = new Configuration()
                .addAnnotatedClass(Patient.class)
                .addAnnotatedClass(MonitoringSession.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:deferrals;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory()) {
            Long id;
            try (var db = factory.openSession()) {
                var transaction = db.beginTransaction();
                Patient patient = new Patient("Test", "Daily monitoring");
                db.persist(patient);
                MonitoringSession session = new MonitoringSession(patient);
                session.deferField(MonitoringField.PAIN_SCORE);
                session.deferField(MonitoringField.MEDICATION_TAKEN);
                session.setClarifiedField(MonitoringField.APPETITE);
                db.persist(session);
                transaction.commit();
                id = session.getId();
            }
            try (var db = factory.openSession()) {
                var restored = db.find(MonitoringSession.class, id);
                assertThat(restored.isDeferred(MonitoringField.PAIN_SCORE)).isTrue();
                assertThat(restored.isDeferred(MonitoringField.MEDICATION_TAKEN)).isTrue();
                assertThat(restored.isDeferred(MonitoringField.APPETITE)).isFalse();
                assertThat(restored.getClarifiedField()).isEqualTo(MonitoringField.APPETITE);
            }
        }
    }
}
