package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Note;
import com.employeehub.repository.NoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Generic note API — type/entityId is the only thing that varies across
// use sites (LCA today; Employee, Visa, Invoice are the same shape).
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    @Autowired
    private NoteRepository noteRepository;

    @GetMapping
    public ResponseEntity<List<Note>> list(@RequestParam String type, @RequestParam Long entityId) {
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
        return ResponseEntity.ok(noteRepository.findByTypeAndEntityIdOrderByDateDesc(type, entityId));
    }

    @PostMapping
    public ResponseEntity<Note> create(@RequestBody Note note) {
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
        note.setNoteId(null); // always a new row — never trust a client-supplied id here
        return new ResponseEntity<>(noteRepository.save(note), HttpStatus.CREATED);
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<Note> update(@PathVariable Long noteId, @RequestBody Note noteDetails) {
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
        Note existing = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId));
        existing.setDescription(noteDetails.getDescription());
        return ResponseEntity.ok(noteRepository.save(existing));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@PathVariable Long noteId) {
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
        noteRepository.deleteById(noteId);
        return ResponseEntity.noContent().build();
    }
}
