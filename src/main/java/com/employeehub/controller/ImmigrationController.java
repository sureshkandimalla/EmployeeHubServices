package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.dto.EmployeeImmigrationProfile;
import com.employeehub.model.Notes;
import com.employeehub.service.EmployeeImmigrationService;
import com.employeehub.service.NotesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/immigration")
public class ImmigrationController {

    private final EmployeeImmigrationService immigrationService;
    private final NotesService notesService;

    public ImmigrationController(EmployeeImmigrationService immigrationService, NotesService notesService) {
        this.immigrationService = immigrationService;
        this.notesService = notesService;
    }

    /**
     * GET /api/v1/immigration/getAllEmployeesImmigration
     * Returns the full immigration profile for every employee.
     */
    @GetMapping("/getAllEmployeesImmigration")
    public ResponseEntity<List<EmployeeImmigrationProfile>> getAllEmployeesImmigration() {
        List<EmployeeImmigrationProfile> profiles = immigrationService.getAllEmployeesImmigration();
        return ResponseEntity.ok(profiles);
    }

    /**
     * GET /api/v1/immigration/getEmployeeImmigration/{id}
     * Returns the immigration profile for a single employee.
     */
    @GetMapping("/getEmployeeImmigration/{id}")
    public ResponseEntity<EmployeeImmigrationProfile> getEmployeeImmigration(@PathVariable long id) {
        return immigrationService.getEmployeeImmigration(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Notes ────────────────────────────────────────────────────────────────

    @GetMapping("/notes/employee/{employeeId}")
    public List<Notes> getNotesByEmployee(@PathVariable Long employeeId) {
        return notesService.getNotesByEmployeeId(employeeId);
    }

    @GetMapping("/notes/{id}")
    public ResponseEntity<Notes> getNoteById(@PathVariable Long id) {
        return ResponseEntity.ok(notesService.getNoteById(id));
    }

    @PostMapping("/notes")
    public ResponseEntity<Notes> createNote(@RequestBody Notes note) {
        // Notes attach to many entity types across every department (Wage,
        // Visa, Employee, ...) — any assigned role may add one, not just
        // Immigration despite this controller's name.
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
        return ResponseEntity.ok(notesService.saveNote(note));
    }

    @PutMapping("/notes/{id}")
    public ResponseEntity<Notes> updateNote(@PathVariable Long id, @RequestBody Notes note) {
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
        return ResponseEntity.ok(notesService.updateNote(id, note));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteNote(@PathVariable Long id) {
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
        notesService.deleteNote(id);
        Map<String, Boolean> response = new HashMap<>();
        response.put("deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }
}
