package com.carevoice.history;
import com.carevoice.service.DeterministicClinicalNoteService;
import com.carevoice.service.MonitoringResponseService;

import com.carevoice.dto.monitoring.ClinicalAgentResponse;
import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.MonitoringSession;
import com.carevoice.domain.Patient;
import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carevoice.ai.enabled=false",
        "carevoice.voice.enabled=false"
})
class ClinicalNoteAsyncTest {
    @Autowired MonitoringResponseService responses;
    @Autowired PatientRepository patients;
    @Autowired MonitoringSessionRepository sessions;
    @Autowired ClinicalNoteRepository notes;
    @MockitoBean DeterministicClinicalNoteService deterministicNotes;

    @Test
    void patientResponseReturnsBeforeClinicalNoteGenerationFinishes() throws Exception {
        Patient patient = patients.save(new Patient("Daily Check-In", "Daily monitoring"));
        MonitoringSession session = new MonitoringSession(patient);
        session.setNextQuestion("Tell me how you are feeling today in your own words.");
        Long sessionId = sessions.save(session).getId();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(deterministicNotes.provider()).thenReturn("deterministic");
        when(deterministicNotes.write(any(), any(), any())).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                return "timed out";
            }
            return "Scheduled note.";
        });

        CompletableFuture<ClinicalAgentResponse> pending = CompletableFuture.supplyAsync(
                () -> responses.acceptText(sessionId, "I feel dizzy today and my pain is about six."));

        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        ClinicalAgentResponse response = pending.get(1, TimeUnit.SECONDS);
        assertThat(response.nextQuestion()).isEqualTo("When did the dizziness start?");

        List<ClinicalNote> saved = notes.findByMonitoringTurn_MonitoringSession_Id(sessionId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getNoteText()).isNull();
        assertThat(saved.get(0).getExtractedFactsJson()).contains("\"painScore\":6");

        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        String noteText = null;
        while (System.nanoTime() < deadline) {
            noteText = notes.findByMonitoringTurn_MonitoringSession_Id(sessionId).get(0).getNoteText();
            if ("Scheduled note.".equals(noteText)) {
                break;
            }
            Thread.sleep(25);
        }
        assertThat(noteText).isEqualTo("Scheduled note.");
        assertThat(notes.findByMonitoringTurn_MonitoringSession_Id(sessionId).get(0).getExtractedFactsJson())
                .contains("\"painScore\":6");
    }
}
