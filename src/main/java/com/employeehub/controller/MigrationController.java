package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-off utility for reassigning an employee's primary key across every
 * table that references it, when an employee ended up with an ID outside
 * their company's reserved range (see CompanyIdRangeConfig) before that was
 * fixed. Not a permanent feature — remove after use. ADMIN-only: this
 * rewrites every HR/payroll/visa/immigration record tied to an employee id
 * in one shot, so it's restricted the same as any other irreversible
 * bulk-data operation.
 */
@RestController
@RequestMapping("/api/v1/migration")
public class MigrationController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MigrationController.class);

    @PersistenceContext
    private EntityManager entityManager;

    @PostMapping("/reassignEmployeeId")
    @Transactional
    public ResponseEntity<Map<String, Object>> reassignEmployeeId(
            @RequestParam long oldId, @RequestParam long newId) {

        CurrentUserContext.requireRole("ADMIN");
        Map<String, Object> updatedRowCounts = new LinkedHashMap<>();

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS=0").executeUpdate();

        updatedRowCounts.put("lca", update("UPDATE lca SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("Address", update("UPDATE Address SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("adjustments_from", update("UPDATE adjustments SET from_id=?2 WHERE from_id=?1", oldId, newId));
        updatedRowCounts.put("adjustments_to", update("UPDATE adjustments SET to_id=?2 WHERE to_id=?1", oldId, newId));
        updatedRowCounts.put("Project", update("UPDATE Project SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("Notes", update("UPDATE Notes SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("Visa", update("UPDATE Visa SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("employee_onboarding", update("UPDATE employee_onboarding SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("passport", update("UPDATE passport SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("Bills", update("UPDATE Bills SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("payroll", update("UPDATE payroll SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("expenses", update("UPDATE expenses SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("payroll_summary", update("UPDATE payroll_summary SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("Assignment", update("UPDATE Assignment SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("payments", update("UPDATE payments SET employee_id=?2 WHERE employee_id=?1", oldId, newId));
        updatedRowCounts.put("Insurance", update("UPDATE Insurance SET employee_id=?2 WHERE employee_id=?1", oldId, newId));

        // Parent row last.
        updatedRowCounts.put("employees", update("UPDATE employees SET employee_id=?2 WHERE employee_id=?1", oldId, newId));

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS=1").executeUpdate();

        logger.info("reassignEmployeeId: {} -> {} : {}", oldId, newId, updatedRowCounts);
        return ResponseEntity.ok(updatedRowCounts);
    }

    private int update(String sql, long oldId, long newId) {
        return entityManager.createNativeQuery(sql)
                .setParameter(1, oldId)
                .setParameter(2, newId)
                .executeUpdate();
    }
}
