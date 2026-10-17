package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.model.LCA;
import com.employeehub.service.LCAService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/lca")
public class LCAController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LCAController.class);

    @Autowired
    private LCAService lcaService;

    @GetMapping
    public List<LCA> getAllLCAs() {
        return lcaService.getAllLCAs();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LCA> getLCAById(@PathVariable Long id) {
        return lcaService.getLCAById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{employeeId}")
    public List<LCA> getLCAsByEmployeeId(@PathVariable Long employeeId) {
        return lcaService.getLCAsByEmployeeId(employeeId);
    }

    @PostMapping("/save")
    public ResponseEntity<LCA> saveLCA(@RequestBody LCA lca) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        boolean isUpdate = lca.getLcaId() != 0;
        logger.info("{} LCA id={}", isUpdate ? "Updating" : "Creating", lca.getLcaId());
        return ResponseEntity.ok(lcaService.saveLCA(lca));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteLCA(@PathVariable Long id) {
        CurrentUserContext.requireRole("ADMIN", "IMMIGRATION");
        lcaService.deleteLCA(id);
        Map<String, Boolean> response = new HashMap<>();
        response.put("deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }
}
