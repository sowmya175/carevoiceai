package com.carevoice.history;

import com.carevoice.domain.ClinicalNote;
import com.carevoice.domain.MonitoringTurn;
import com.carevoice.repository.ClinicalNoteRepository;
import com.carevoice.repository.MonitoringSessionRepository;
import com.carevoice.repository.MonitoringTurnRepository;
import com.carevoice.service.ClinicalMonitoringAgent;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class HistoryTestSupport {
    public final List<MonitoringTurn> turns = new ArrayList<>();
    public final List<ClinicalNote> notes = new ArrayList<>();
    public final MonitoringResponseService responses;

    private HistoryTestSupport(
            MonitoringSessionRepository sessions,
            ClinicalMonitoringAgent agent,
            GeminiClinicalNoteService gemini) {
        MonitoringTurnRepository turnRepository = mock(MonitoringTurnRepository.class);
        ClinicalNoteRepository noteRepository = mock(ClinicalNoteRepository.class);
        AtomicInteger sequence = new AtomicInteger();
        when(turnRepository.maxSequence(any())).thenAnswer(invocation -> sequence.get());
        when(turnRepository.save(any())).thenAnswer(invocation -> {
            MonitoringTurn turn = invocation.getArgument(0);
            setId(turn, (long) turn.getSequenceNumber());
            turns.add(turn);
            sequence.set(turn.getSequenceNumber());
            return turn;
        });
        when(noteRepository.save(any())).thenAnswer(invocation -> {
            ClinicalNote note = invocation.getArgument(0);
            setId(note, (long) notes.size() + 1);
            notes.add(note);
            return note;
        });
        when(noteRepository.findById(any())).thenAnswer(invocation -> notes.stream()
                .filter(note -> invocation.getArgument(0).equals(note.getId()))
                .findFirst());

        @SuppressWarnings("unchecked")
        ObjectProvider<GeminiClinicalNoteService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gemini);
        DeterministicClinicalNoteService deterministic = new DeterministicClinicalNoteService();
        ClinicalNoteAttacher attacher = new ClinicalNoteAttacher(
                new ClinicalNoteComposer(provider, deterministic),
                deterministic,
                new ClinicalNoteWriter(noteRepository));
        this.responses = new MonitoringResponseService(
                new MonitoringHistoryRecorder(sessions, turnRepository, noteRepository, agent),
                attacher);
    }

    public static MonitoringResponseService responses(
            MonitoringSessionRepository sessions,
            ClinicalMonitoringAgent agent) {
        return capture(sessions, agent, null).responses;
    }

    public static HistoryTestSupport capture(
            MonitoringSessionRepository sessions,
            ClinicalMonitoringAgent agent,
            GeminiClinicalNoteService gemini) {
        return new HistoryTestSupport(sessions, agent, gemini);
    }

    public static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public Optional<ClinicalNote> noteFor(int sequenceNumber) {
        return notes.stream()
                .filter(note -> note.getMonitoringTurn().getSequenceNumber() == sequenceNumber)
                .findFirst();
    }
}
