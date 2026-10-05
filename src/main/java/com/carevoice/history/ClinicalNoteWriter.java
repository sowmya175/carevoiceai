package com.carevoice.history;

import com.carevoice.domain.ClinicalNote;
import com.carevoice.repository.ClinicalNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalNoteWriter {
    private final ClinicalNoteRepository notes;

    public ClinicalNoteWriter(ClinicalNoteRepository notes) {
        this.notes = notes;
    }

    @Transactional
    public void save(Long noteId, NoteDraft draft) {
        ClinicalNote note = notes.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Clinical note not found: " + noteId));
        note.setNoteText(draft.text());
        note.setNoteProvider(draft.provider());
        note.setModel(draft.model());
    }
}
