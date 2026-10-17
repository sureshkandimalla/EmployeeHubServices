package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.model.HealthInsurance;
import com.employeehub.repository.HealthInsuranceRepository;
import com.employeehub.service.HealthInsuranceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/healthinsurance")
public class HealthInsuranceController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HealthInsuranceController.class);

    @Autowired
    private HealthInsuranceService healthInsuranceService;

    @Autowired
    private HealthInsuranceRepository healthInsuranceRepository;

    @GetMapping("/getAll")
    public List<HealthInsurance> getAll() {
        return healthInsuranceService.getAll();
    }

    @GetMapping("/getByEmployee")
    public List<HealthInsurance> getByEmployee(@RequestParam String employeeName) {
        return healthInsuranceService.getByEmployeeName(employeeName);
    }

    @GetMapping("/getByEmployeeId/{employeeId}")
    public List<HealthInsurance> getByEmployeeId(@PathVariable Long employeeId) {
        return healthInsuranceService.getByEmployeeId(employeeId);
    }

    @GetMapping("/getByDateRange")
    public List<HealthInsurance> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return healthInsuranceService.getByDateRange(startDate, endDate);
    }

    @PostMapping("/import")
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        try {
            HealthInsuranceService.CsvImportResult result = healthInsuranceService.importFromCsv(file);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("imported", result.saved.size());
            response.put("unmatchedEmployeeCount", result.unmatchedEmployeeNames.size());
            response.put("unmatchedEmployeeNames", result.unmatchedEmployeeNames);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to import HealthInsurance CSV", e);
            return ResponseEntity.badRequest().body("Import failed: " + e.getMessage());
        }
    }

    @PostMapping("/importFromResource")
    public ResponseEntity<?> importFromResource() {
        try {
            HealthInsuranceService.CsvImportResult result = healthInsuranceService.importFromResource();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("imported", result.saved.size());
            response.put("unmatchedEmployeeCount", result.unmatchedEmployeeNames.size());
            response.put("unmatchedEmployeeNames", result.unmatchedEmployeeNames);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to import bundled HealthInsurance.csv", e);
            return ResponseEntity.badRequest().body("Import failed: " + e.getMessage());
        }
    }

    @PostMapping("/reprocessUnmatched")
    public ResponseEntity<?> reprocessUnmatched() {
        int updated = healthInsuranceService.reprocessUnmatched();
        return ResponseEntity.ok("Updated employeeId for " + updated + " previously unmatched record(s).");
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<?> deleteAll() {
        CurrentUserContext.requireRole("ADMIN");
        healthInsuranceRepository.deleteAll();
        return ResponseEntity.ok("All health insurance records deleted.");
    }
}
