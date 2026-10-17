package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.model.PotentialEmployee;
import com.employeehub.model.Visa;
import com.employeehub.repository.PemployeeRepository;
import com.employeehub.service.VisaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/visa")
public class VisaController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VisaController.class);

    @Autowired
    private VisaService visaService;

    @Autowired
    private PemployeeRepository pemployeeRepository;

    // ── Visa CRUD ────────────────────────────────────────────────────────────────

    @GetMapping("/getAllVisas")
    public List<Visa> getAllVisas() {
        return visaService.getAllVisas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Visa> getVisaById(@PathVariable Long id) {
        return visaService.getVisaById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{employeeId}")
    public List<Visa> getVisasByEmployee(@PathVariable Long employeeId) {
        return visaService.getVisasByEmployeeId(employeeId);
    }

    @GetMapping("/employee/{employeeId}/latest")
    public ResponseEntity<Visa> getLatestVisaByEmployee(@PathVariable Long employeeId) {
        return visaService.getLatestVisaByEmployeeId(employeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/createVisa")
    public ResponseEntity<Visa> createVisa(@RequestBody Visa visa) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        logger.info("Creating visa: {}", visa);
        return ResponseEntity.ok(visaService.saveVisa(visa));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Visa> updateVisa(@PathVariable Long id, @RequestBody Visa visaDetails) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        logger.info("Updating visa id={}", id);
        return ResponseEntity.ok(visaService.updateVisa(id, visaDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteVisa(@PathVariable Long id) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        visaService.deleteVisa(id);
        Map<String, Boolean> response = new HashMap<>();
        response.put("deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }

    // ── Potential Employees ──────────────────────────────────────────────────────

    @GetMapping("/getAllPotentialEmployees")
    public List<PotentialEmployee> getAllPotentialEmployees() {
        return pemployeeRepository.findAll();
    }

    @PostMapping("/potentialEmployees")
    public ResponseEntity<PotentialEmployee> createPotentialEmployee(@RequestBody PotentialEmployee potentialEmployee) {
        CurrentUserContext.requireRole("ADMIN", "HR");
        logger.info("Creating potential employee: {}", potentialEmployee);
        return ResponseEntity.ok(pemployeeRepository.save(potentialEmployee));
    }

    @PostMapping("/savePotentialEmployees")
    public ResponseEntity<List<PotentialEmployee>> savePotentialEmployees(@RequestBody List<PotentialEmployee> potentialEmployees) {
        CurrentUserContext.requireRole("ADMIN", "HR");
        return ResponseEntity.ok(pemployeeRepository.saveAll(potentialEmployees));
    }

    @Transactional
    @DeleteMapping("/potentialEmployees/{id}")
    public ResponseEntity<Map<String, Boolean>> deletePotentialEmployee(@PathVariable Long id) {
        CurrentUserContext.requireRole("ADMIN", "HR");
        pemployeeRepository.deleteByPeId(id);
        Map<String, Boolean> response = new HashMap<>();
        response.put("deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }
}
