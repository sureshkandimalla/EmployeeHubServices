package com.employeehub.controller;

import com.employeehub.dto.EmployeeImmigrationProfile;
import com.employeehub.service.EmployeeImmigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/immigration")
public class ImmigrationController {

    private final EmployeeImmigrationService immigrationService;

    public ImmigrationController(EmployeeImmigrationService immigrationService) {
        this.immigrationService = immigrationService;
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
}
