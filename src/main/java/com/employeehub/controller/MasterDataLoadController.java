package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.service.MasterDataLoadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Backs the "Master Data Load" page (Employees, Customers, Projects,
 * Assignments — one Excel template download + one Excel upload per entity).
 * Invoices are deliberately not included here: the app already generates
 * them from Project + Assignment data (see ProjectService's invoice-period
 * logic), so bulk-importing raw invoice rows would risk duplicates.
 */
@RestController
@RequestMapping("/api/v1/masterdata")
public class MasterDataLoadController {

    @Autowired
    private MasterDataLoadService masterDataLoadService;

    @GetMapping("/template/{entityType}")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String entityType) throws Exception {
        byte[] workbook = switch (entityType) {
            case "employees" -> masterDataLoadService.buildEmployeeTemplate();
            case "customers" -> masterDataLoadService.buildCustomerTemplate();
            case "projects" -> masterDataLoadService.buildProjectTemplate();
            case "assignments" -> masterDataLoadService.buildAssignmentTemplate();
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entityType + "_template.xlsx\"")
                .body(workbook);
    }

    @PostMapping("/import/{entityType}")
    public ResponseEntity<MasterDataLoadService.ImportResult> importData(
            @PathVariable String entityType, @RequestParam("file") MultipartFile file) throws Exception {
        CurrentUserContext.requireRole("ADMIN");
        MasterDataLoadService.ImportResult result = switch (entityType) {
            case "employees" -> masterDataLoadService.importEmployees(file);
            case "customers" -> masterDataLoadService.importCustomers(file);
            case "projects" -> masterDataLoadService.importProjects(file);
            case "assignments" -> masterDataLoadService.importAssignments(file);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
        return ResponseEntity.ok(result);
    }
}
