package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.*;
import com.employeehub.repository.ProjectRepository;
import com.employeehub.repository.WageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/wages")
public class WageController {
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WageController.class);
	
	@Autowired
    private WageRepository wageRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @GetMapping("/getAllWages")
    public List<Wage> getAllEmployees() {
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING");
        return wageRepository.findAll();
    }
    @PostMapping("/wage")
    public ResponseEntity<Map<String, Object>> createAssignment(@RequestBody com.employeehub.domain.Wage domainWage) {
        // ProjectOnBoarding (HR-only route) and WorkOrderForm (Accounting's
        // /projects) both create wage/bill-rate rows here.
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING");
        logger.debug("createAssignment wage: {}", domainWage);
        Project project = projectRepository.findById(domainWage.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not exist with id :" + domainWage.getProjectId()));
        Wage modelWage=new Wage();
        modelWage.setWage(domainWage.getWage());
        modelWage.setWageType(domainWage.getWageType());
        modelWage.setStartDate(domainWage.getStartDate());
        modelWage.setEndDate(domainWage.getEndDate());
        project.getBillRates().add(modelWage);
        modelWage.setCreatedDate(LocalDate.now());

        Project savedProject = projectRepository.save(project);
        // wageId is returned so the frontend can immediately attach a
        // document (Purchase Order) to this specific work order —
        // DocumentsPanel's presign/confirm flow needs a real entityId.
        Map<String, Object> response = new HashMap<>();
        response.put("project", savedProject);
        response.put("wageId", modelWage.getWageId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Wage> updateWage(@PathVariable long id, @RequestBody Wage wageDetails) {
        // ProjectOnBoarding (HR-only route) also updates wages here.
        CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING");
        Wage wage = wageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wage not exist with id :" + id));

        wage.setWage(wageDetails.getWage());
        wage.setStartDate(wageDetails.getStartDate());
        wage.setEndDate(wageDetails.getEndDate());

        return ResponseEntity.ok(wageRepository.save(wage));
    }

}
