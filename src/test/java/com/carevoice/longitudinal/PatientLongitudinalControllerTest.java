package com.carevoice.longitudinal;
import com.carevoice.service.LongitudinalAnalysisService;
import com.carevoice.domain.Patient;
import com.carevoice.domain.SessionFactsSnapshot;

import com.carevoice.controller.PatientLongitudinalController;
import com.carevoice.exception.ApiExceptionHandler;
import com.carevoice.config.CareVoiceVoiceProperties;
import com.carevoice.domain.RiskLevel;
import com.carevoice.domain.SessionStatus;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PatientLongitudinalControllerTest {
    private final PatientRepository patients = mock(PatientRepository.class);
    private final MonitoringSessionRepository sessions = mock(MonitoringSessionRepository.class);
    private final MonitoringTurnRepository turns = mock(MonitoringTurnRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(patients.existsById(7L)).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new PatientLongitudinalController(
                        new LongitudinalAnalysisService(patients, sessions, turns)))
                .setControllerAdvice(new ApiExceptionHandler(new CareVoiceVoiceProperties())).build();
    }

    @Test
    void returnsEmptyResponseWithDefaultLimit() throws Exception {
        mvc.perform(get("/api/patients/7/longitudinal-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(7))
                .andExpect(jsonPath("$.sessionCount").value(0))
                .andExpect(jsonPath("$.windowStart").value(nullValue()))
                .andExpect(jsonPath("$.latestCheckInAt").value(nullValue()))
                .andExpect(jsonPath("$.pain.change").value(nullValue()))
                .andExpect(jsonPath("$.pain.observations").isEmpty())
                .andExpect(jsonPath("$.sessions").isEmpty());
        verify(sessions).findRecentFacts(eq(7L), anyList(), eq(PageRequest.of(0, 30)));
    }

    @Test
    void returnsStructuredResponseWithCustomLimitAndNoDetailedHistory() throws Exception {
        var timestamp = OffsetDateTime.parse("2026-10-01T09:00:00-04:00");
        when(sessions.findRecentFacts(eq(7L), anyList(), any())).thenReturn(List.of(new SessionFactsSnapshot(
                101L, timestamp, SessionStatus.READY_FOR_REVIEW, RiskLevel.YELLOW,
                2, "poor", "reduced", false, true, null, false, "This morning.", 98.6, null, null)));
        when(turns.countByPatientAndSessionIds(7L, List.of(101L))).thenReturn(List.<Object[]>of(new Object[]{101L, 3L}));
        mvc.perform(get("/api/patients/7/longitudinal-summary").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCount").value(1))
                .andExpect(jsonPath("$.pain.observations[0].sessionId").value(101))
                .andExpect(jsonPath("$.pain.observations[0].value").value(2))
                .andExpect(jsonPath("$.pain.observations[0].timestamp").isString())
                .andExpect(jsonPath("$.sleep.poorCount").value(1))
                .andExpect(jsonPath("$.appetite.reducedCount").value(1))
                .andExpect(jsonPath("$.medication.missedCount").value(1))
                .andExpect(jsonPath("$.symptoms.dizziness.reportedTrueCount").value(1))
                .andExpect(jsonPath("$.symptoms.shortnessOfBreath.unknownCount").value(1))
                .andExpect(jsonPath("$.symptoms.shortnessOfBreath.observations[0].value").value(nullValue()))
                .andExpect(jsonPath("$.symptoms.lossOfConsciousness.reportedFalseCount").value(1))
                .andExpect(jsonPath("$.dizzinessOnset[0].onset").value("This morning."))
                .andExpect(jsonPath("$.temperature[0].value").value(98.6))
                .andExpect(jsonPath("$.sessions[0].turnCount").value(3))
                .andExpect(jsonPath("$.sessions[0].status").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.sessions[0].riskLevel").value("YELLOW"))
                .andExpect(jsonPath("$.sessions[0].latestTranscript").doesNotExist())
                .andExpect(jsonPath("$.sessions[0].turns").doesNotExist())
                .andExpect(jsonPath("$.sessions[0].clinicalNote").doesNotExist())
                .andExpect(content().json(Files.readString(Path.of("docs", "longitudinal-summary.example.json"))));
        verify(sessions).findRecentFacts(eq(7L), anyList(), eq(PageRequest.of(0, 1)));
    }

    @Test
    void missingPatientReturnsExisting404ErrorShape() throws Exception {
        mvc.perform(get("/api/patients/99/longitudinal-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Patient not found: 99"));
        verifyNoInteractions(sessions, turns);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc", "999999999999999"})
    void invalidLimitReturns400WithoutReadingSessions(String limit) throws Exception {
        mvc.perform(get("/api/patients/7/longitudinal-summary").param("limit", limit))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sessions, turns);
    }
}
