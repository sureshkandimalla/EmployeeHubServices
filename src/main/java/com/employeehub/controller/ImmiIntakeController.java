package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.model.ImmiIntake;
import com.employeehub.repository.ImmiIntakeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/immiIntake")
public class ImmiIntakeController {

    @Autowired
    private ImmiIntakeRepository immiIntakeRepository;

    @GetMapping("/getAll")
    public List<ImmiIntake> getAll() {
        return immiIntakeRepository.findAll();
    }

    @GetMapping("/employee/{employeeId}")
    public List<ImmiIntake> getByEmployeeId(@PathVariable Long employeeId) {
        return immiIntakeRepository.findByEmployeeId(employeeId);
    }

    @PostMapping("/create")
    public ResponseEntity<ImmiIntake> create(@RequestBody ImmiIntake immiIntake) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        return ResponseEntity.ok(immiIntakeRepository.save(immiIntake));
    }

    @PutMapping("/{intakeId}")
    public ResponseEntity<ImmiIntake> update(@PathVariable Long intakeId, @RequestBody ImmiIntake immiIntake) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        immiIntake.setIntakeId(intakeId);
        return ResponseEntity.ok(immiIntakeRepository.save(immiIntake));
    }

    @DeleteMapping("/{intakeId}")
    public ResponseEntity<Void> delete(@PathVariable Long intakeId) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        immiIntakeRepository.deleteById(intakeId);
        return ResponseEntity.noContent().build();
    }
}
