package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.I9ValidationException;
import com.employeehub.i9.I9Rules;
import com.employeehub.model.i9.I9Record;
import com.employeehub.repository.I9RecordRepository;
import com.employeehub.service.I9RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

// Form I-9 records — access is locked to HR/IMMIGRATION/ADMIN on every
// endpoint (I-9 is the most sensitive document category: SSN, A-numbers,
// work authorization), matching the access just granted to the Employee
// Documents tabs this session. Validation always re-runs server-side
// (I9RecordService#save) — a 422 here carries the full
// I9Rules.ValidationResult so the frontend can show every error/warning
// inline, not just the first one.
@RestController
@RequestMapping("/api/v1/i9records")
public class I9RecordController {

    @Autowired
    private I9RecordService i9RecordService;
    @Autowired
    private I9RecordRepository i9RecordRepository;

    private void requireAccess() {
        CurrentUserContext.requireRole("ADMIN", "HR", "IMMIGRATION");
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<I9RecordService.EmployeeSummary> getForEmployee(@PathVariable Long employeeId) {
        requireAccess();
        return ResponseEntity.ok(i9RecordService.getForEmployee(employeeId));
    }

    @PostMapping("/validate")
    public ResponseEntity<I9Rules.ValidationResult> validate(@RequestBody I9Record record) {
        requireAccess();
        return ResponseEntity.ok(i9RecordService.validate(record));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody I9Record record) {
        requireAccess();
        record.setId(null);
        return save(record);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody I9Record record) {
        requireAccess();
        record.setId(id);
        return save(record);
    }

    private ResponseEntity<?> save(I9Record record) {
        try {
            return new ResponseEntity<>(i9RecordService.save(record), HttpStatus.OK);
        } catch (I9ValidationException e) {
            return ResponseEntity.unprocessableEntity().body(e.getResult());
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<I9Rules.Task>> tasks() {
        requireAccess();
        return ResponseEntity.ok(i9RecordService.openTasksForAllEmployees());
    }

    // Extraction-only — never persists anything. The frontend uses the
    // result purely to pre-fill the Add form; the user still reviews and
    // submits it through the normal create/validate flow above.
    @PostMapping("/employee/{employeeId}/extractFromPdf")
    public ResponseEntity<?> extractFromPdf(@PathVariable Long employeeId, @RequestParam("file") MultipartFile file) {
        requireAccess();
        try {
            return ResponseEntity.ok(i9RecordService.extractFromPdf(employeeId, file.getInputStream()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Could not read that PDF: " + e.getMessage());
        }
    }

    @GetMapping("/employee/{employeeId}/forCorrection")
    public ResponseEntity<List<I9Record>> recordsAvailableForCorrection(@PathVariable Long employeeId) {
        requireAccess();
        return ResponseEntity.ok(i9RecordRepository.findByEmployeeIdOrderByValidFromDesc(employeeId));
    }
}
