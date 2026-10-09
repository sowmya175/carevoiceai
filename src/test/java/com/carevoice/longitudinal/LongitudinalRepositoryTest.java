package com.carevoice.longitudinal;

import com.carevoice.domain.*;
import com.carevoice.history.MonitoringHistoryQuery;
import com.carevoice.repository.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LongitudinalRepositoryTest {
    private static SessionFactory factory;
    private Session db;
    private Patient patient;
    private Patient otherPatient;
    private LongitudinalAnalysisService service;
    private MonitoringHistoryQuery history;

    @BeforeAll
    static void startDatabase() {
        factory = new Configuration()
                .addAnnotatedClass(Patient.class).addAnnotatedClass(MonitoringSession.class)
                .addAnnotatedClass(MonitoringPlan.class).addAnnotatedClass(SessionPlanQuestion.class)
                .addAnnotatedClass(MonitoringTurn.class).addAnnotatedClass(ClinicalNote.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:longitudinal;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .setProperty("hibernate.generate_statistics", "true")
                .buildSessionFactory();
    }

    @AfterAll
    static void closeDatabase() {
        factory.close();
    }

    @BeforeEach
    void createPatientAndRepositories() {
        db = factory.openSession();
        db.beginTransaction();
        patient = new Patient("Test", "Daily monitoring");
        otherPatient = new Patient("Other", "Daily monitoring");
        db.persist(patient);
        db.persist(otherPatient);
        var repositories = new JpaRepositoryFactory(db);
        var patients = repositories.getRepository(PatientRepository.class);
        var sessions = repositories.getRepository(MonitoringSessionRepository.class);
        var turns = repositories.getRepository(MonitoringTurnRepository.class);
        var notes = repositories.getRepository(ClinicalNoteRepository.class);
        service = new LongitudinalAnalysisService(patients, sessions, turns);
        history = new MonitoringHistoryQuery(sessions, turns, notes, patients);
    }

    @AfterEach
    void rollbackFixture() {
        db.getTransaction().rollback();
        db.close();
    }

    @Test
    void selectsOnlyRecentEligibleSessionsForThisPatientThenReturnsThemChronologically() {
        var first = session(patient, 1, SessionStatus.COMPLETED, 2);
        var second = session(patient, 2, SessionStatus.READY_FOR_REVIEW, 4);
        var third = session(patient, 3, SessionStatus.COMPLETED, 6);
        session(patient, 4, SessionStatus.IN_PROGRESS, 10);
        session(otherPatient, 5, SessionStatus.COMPLETED, 9);
        readFixture();

        var response = service.summarize(patient.getId(), 2);

        assertThat(response.sessionCount()).isEqualTo(2);
        assertThat(response.sessions()).extracting(row -> row.sessionId()).containsExactly(second.getId(), third.getId());
        assertThat(response.sessions()).extracting(row -> row.status())
                .containsExactly(SessionStatus.READY_FOR_REVIEW, SessionStatus.COMPLETED);
        assertThat(response.pain().observations()).extracting(PatientLongitudinalResponse.Observation::value)
                .containsExactly(4, 6);
        assertThat(response.pain().firstValue()).isEqualTo(4);
        assertThat(response.pain().change()).isEqualTo(2);
        assertThat(response.windowStart().toInstant()).isEqualTo(second.getCreatedAt().toInstant());
        assertThat(response.latestCheckInAt().toInstant()).isEqualTo(third.getCreatedAt().toInstant());
        assertThat(response.sessions()).noneMatch(row -> row.sessionId().equals(first.getId()));
    }

    @Test
    void patientWithOnlyAnActiveSessionHasAnEmptySummary() {
        session(patient, 1, SessionStatus.IN_PROGRESS, 8);
        readFixture();
        var response = service.summarize(patient.getId(), 30);
        assertThat(response.sessionCount()).isZero();
        assertThat(response.pain().observations()).isEmpty();
        assertThat(response.latestCheckInAt()).isNull();
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void tiedStartTimesUseIdAsAStableTieBreaker() {
        var first = session(patient, 1, SessionStatus.COMPLETED, 2);
        var second = session(patient, 1, SessionStatus.COMPLETED, 4);
        var third = session(patient, 1, SessionStatus.COMPLETED, 6);
        readFixture();
        var response = service.summarize(patient.getId(), 2);
        assertThat(response.sessions()).extracting(row -> row.sessionId()).containsExactly(second.getId(), third.getId());
        assertThat(response.sessions()).noneMatch(row -> row.sessionId().equals(first.getId()));
    }

    @Test
    void limitsInSqlAndUsesThreeReadsWithoutLoadingEntitiesOrTranscripts() {
        for (int i = 1; i <= 35; i++) {
            var session = session(patient, i, SessionStatus.COMPLETED, i % 11);
            turn(session, 1, "This transcript is not analysis input.");
            turn(session, 2, "Nor is this transcript.");
        }
        readFixture();
        var response = service.summarize(patient.getId(), 30);
        assertThat(response.sessionCount()).isEqualTo(30);
        assertThat(response.windowStart().toInstant()).isEqualTo(date(6).toInstant());
        assertThat(response.sessions()).allSatisfy(row -> assertThat(row.turnCount()).isEqualTo(2));
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(3);
        assertThat(factory.getStatistics().getEntityLoadCount()).isZero();
        assertThat(factory.getStatistics().getCollectionFetchCount()).isZero();
        assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
    }

    @Test
    void sessionFactsAreAuthoritativeAndHistoryRemainsUnchanged() {
        var session = session(patient, 1, SessionStatus.COMPLETED, 2);
        session.setAppetite("normal");
        session.setMedicationTaken(null);
        session.setDizzinessOnset("  When I stood up.  ");
        session.setLatestTranscript("I said pain was nine.");
        var turn = turn(session, 1, "  Original patient transcript: pain nine.  ");
        var note = new ClinicalNote(turn, "{\"painScore\":9,\"medicationTaken\":false}", RiskLevel.YELLOW, "Original reason");
        note.setNoteText("Narrative says pain nine and missed medication.");
        db.persist(note);
        ReflectionTestUtils.setField(session, "updatedAt", date(1).plusHours(1));
        var updated = session.getUpdatedAt();
        readFixture();

        var response = service.summarize(patient.getId(), 30);
        assertThat(response.pain().latestValue()).isEqualTo(2);
        assertThat(response.medication().unknownCount()).isEqualTo(1);
        assertThat(response.medication().missedCount()).isZero();
        assertThat(response.dizzinessOnset().getFirst().onset()).isEqualTo("  When I stood up.  ");
        db.flush();
        assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
        assertThat(factory.getStatistics().getEntityInsertCount()).isZero();
        assertThat(factory.getStatistics().getEntityDeleteCount()).isZero();
        db.clear();
        var storedSession = db.find(MonitoringSession.class, session.getId());
        assertThat(storedSession.getUpdatedAt().toInstant()).isEqualTo(updated.toInstant());
        assertThat(storedSession.getLatestTranscript()).isEqualTo("I said pain was nine.");
        assertThat(db.find(MonitoringTurn.class, turn.getId()).getPatientResponse())
                .isEqualTo("  Original patient transcript: pain nine.  ");
        var storedNote = db.find(ClinicalNote.class, note.getId());
        assertThat(storedNote.getExtractedFactsJson()).isEqualTo("{\"painScore\":9,\"medicationTaken\":false}");
        assertThat(storedNote.getNoteText()).isEqualTo("Narrative says pain nine and missed medication.");

        var detailedHistory = history.sessionHistory(session.getId());
        assertThat(detailedHistory.turns().getFirst().patientResponse()).isEqualTo(turn.getPatientResponse());
        assertThat(detailedHistory.turns().getFirst().extractedFacts()).containsEntry("painScore", 9);
        assertThat(history.patientHistory(patient.getId()).sessions()).hasSize(1);
    }

    private MonitoringSession session(Patient owner, int day, SessionStatus status, int pain) {
        var session = new MonitoringSession(owner);
        session.setStatus(status);
        session.setPainScore(pain);
        ReflectionTestUtils.setField(session, "createdAt", date(day));
        // Database timestamp precision is microseconds; use a stable value for integrity assertions.
        ReflectionTestUtils.setField(session, "updatedAt", date(day).plusHours(1));
        db.persist(session);
        return session;
    }

    private MonitoringTurn turn(MonitoringSession session, int sequence, String response) {
        var turn = new MonitoringTurn(session.getPatient(), session, sequence, "Original question", response, InputMode.TEXT);
        db.persist(turn);
        return turn;
    }

    private void readFixture() {
        db.flush();
        db.clear();
        factory.getStatistics().clear();
    }

    private static OffsetDateTime date(int day) {
        return OffsetDateTime.parse("2026-10-01T09:00:00-04:00").plusDays(day - 1);
    }
}
