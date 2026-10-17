package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.model.Coi;
import com.employeehub.repository.CoiRepository;
import com.employeehub.service.CoiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coi")
public class CoiController {

    @Autowired
    private CoiRepository coiRepository;
    @Autowired
    private CoiService coiService;

    @PostMapping
    public ResponseEntity<Coi> createCoi(@RequestBody Coi coi) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
        Coi saved = coiService.create(coi);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Coi> getCoiById(@PathVariable Long id) {
        return ResponseEntity.ok(coiService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coi> updateCoi(@PathVariable Long id, @RequestBody Coi coiDetails) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
        return ResponseEntity.ok(coiService.update(id, coiDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoi(@PathVariable Long id) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
        coiService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/getAllCoi")
    public ResponseEntity<List<Coi>> getAllCoi() {
        return ResponseEntity.ok(coiRepository.findAll());
    }

    @GetMapping("/getCoiForVendor")
    public ResponseEntity<List<Coi>> getCoiForVendor(@RequestParam(required = true) Long vendorId) {
        return ResponseEntity.ok(coiRepository.findByVendorId(vendorId));
    }

    @GetMapping("/getCoiForCustomer")
    public ResponseEntity<List<Coi>> getCoiForCustomer(@RequestParam(required = true) Long customerId) {
        return ResponseEntity.ok(coiRepository.findByCustomerId(customerId));
    }
}
