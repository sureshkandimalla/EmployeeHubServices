package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.model.TimeSheet;
import com.employeehub.repository.TimeSheetRepository;
import com.employeehub.service.TimeSheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/timesheets")
public class TimeSheetController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TimeSheetController.class);

    @Autowired
    private TimeSheetService timeSheetService;

    @Autowired
    private TimeSheetRepository timeSheetRepository;

    @GetMapping("/getByAssignmentAndMonth")
    public List<TimeSheet> getByAssignmentAndMonth(@RequestParam Long assignmentId, @RequestParam String yearMonth) {
        return timeSheetService.getByAssignmentAndMonth(assignmentId, YearMonth.parse(yearMonth));
    }

    @GetMapping("/getByEmployeeAndMonth")
    public List<TimeSheet> getByEmployeeAndMonth(@RequestParam Long employeeId, @RequestParam String yearMonth) {
        return timeSheetService.getByEmployeeAndMonth(employeeId, YearMonth.parse(yearMonth));
    }

    @GetMapping("/getByMonth")
    public List<TimeSheet> getByMonth(@RequestParam String yearMonth) {
        return timeSheetService.getByMonth(YearMonth.parse(yearMonth));
    }

    @GetMapping("/getByEmployeeAndRange")
    public List<TimeSheet> getByEmployeeAndRange(@RequestParam Long employeeId, @RequestParam String startDate, @RequestParam String endDate) {
        return timeSheetService.getByEmployeeAndRange(employeeId, LocalDate.parse(startDate), LocalDate.parse(endDate));
    }

    @GetMapping("/getByRange")
    public List<TimeSheet> getByRange(@RequestParam String startDate, @RequestParam String endDate) {
        return timeSheetService.getByRange(LocalDate.parse(startDate), LocalDate.parse(endDate));
    }

    @PostMapping("/saveBulk")
    public ResponseEntity<?> saveBulk(@RequestBody List<TimeSheet> entries) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        try {
            TimeSheetService.SaveResult result = timeSheetService.saveBulk(entries);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("saved", result.saved.size());
            response.put("lockedDates", result.lockedDates);
            response.put("outOfRangeDates", result.outOfRangeDates);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to save timesheet entries", e);
            return ResponseEntity.badRequest().body("Save failed: " + e.getMessage());
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestParam Long assignmentId, @RequestParam String yearMonth) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        int updated = timeSheetService.submitMonth(assignmentId, YearMonth.parse(yearMonth));
        return ResponseEntity.ok("Submitted " + updated + " day(s) for approval.");
    }

    @PostMapping("/approve")
    public ResponseEntity<?> approve(@RequestParam Long assignmentId, @RequestParam String yearMonth) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        int updated = timeSheetService.approveMonth(assignmentId, YearMonth.parse(yearMonth));
        return ResponseEntity.ok("Approved " + updated + " day(s).");
    }

    @PostMapping("/reopen")
    public ResponseEntity<?> reopen(@RequestParam Long assignmentId, @RequestParam String yearMonth) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        int updated = timeSheetService.reopenMonth(assignmentId, YearMonth.parse(yearMonth));
        return ResponseEntity.ok("Reopened " + updated + " day(s) for editing.");
    }

    // Range-scoped versions of submit/approve/reopen, plus reject — used by
    // the Monthly Timesheets page's per-week action menu, where "a week" is
    // just a date range rather than a whole month.
    @PostMapping("/submitRange")
    public ResponseEntity<?> submitRange(@RequestParam Long assignmentId, @RequestParam String startDate, @RequestParam String endDate) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        int updated = timeSheetService.submitRange(assignmentId, LocalDate.parse(startDate), LocalDate.parse(endDate));
        return ResponseEntity.ok("Submitted " + updated + " day(s) for approval.");
    }

    @PostMapping("/approveRange")
    public ResponseEntity<?> approveRange(@RequestParam Long assignmentId, @RequestParam String startDate, @RequestParam String endDate) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        int updated = timeSheetService.approveRange(assignmentId, LocalDate.parse(startDate), LocalDate.parse(endDate));
        return ResponseEntity.ok("Approved " + updated + " day(s).");
    }

    @PostMapping("/rejectRange")
    public ResponseEntity<?> rejectRange(@RequestParam Long assignmentId, @RequestParam String startDate, @RequestParam String endDate) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        int updated = timeSheetService.rejectRange(assignmentId, LocalDate.parse(startDate), LocalDate.parse(endDate));
        return ResponseEntity.ok("Rejected " + updated + " day(s).");
    }

    @PostMapping("/reopenRange")
    public ResponseEntity<?> reopenRange(@RequestParam Long assignmentId, @RequestParam String startDate, @RequestParam String endDate) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        int updated = timeSheetService.reopenRange(assignmentId, LocalDate.parse(startDate), LocalDate.parse(endDate));
        return ResponseEntity.ok("Reopened " + updated + " day(s) for editing.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING", "IMMIGRATION");
        timeSheetRepository.deleteById(id);
        return ResponseEntity.ok("Deleted timesheet entry " + id);
    }
}
