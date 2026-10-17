package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.domain.EmployeeHoursReportRow;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Bills;
import com.employeehub.model.Employee;
import com.employeehub.model.PayPeriod;
import com.employeehub.model.PayrollSummary;
import com.employeehub.model.TimeSheet;
import com.employeehub.repository.BillsRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.PayPeriodRepository;
import com.employeehub.repository.PayrollSummaryRepository;
import com.employeehub.repository.TimeSheetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Per-employee, per-pay-period comparison of hours across the three places
// this app records them independently — Payroll (what was paid), TimeSheet
// (what was logged), and Bills (what was invoiced to the client) — so a
// manager can spot-check whether all three agree instead of trusting
// whichever one they happen to be looking at.
@RestController
@RequestMapping("/api/v1/hours-report")
public class HoursReportController {

    // Two sources rarely land on the exact same float (rounding on entry,
    // half-day timesheet corrections, etc.) — only flag a real mismatch,
    // not noise.
    private static final float DISCREPANCY_TOLERANCE_HOURS = 1.0f;

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayPeriodRepository payPeriodRepository;
    @Autowired private PayrollSummaryRepository payrollSummaryRepository;
    @Autowired private TimeSheetRepository timeSheetRepository;
    @Autowired private BillsRepository billsRepository;

    @GetMapping("/getReport")
    public List<EmployeeHoursReportRow> getReport(
            @RequestParam Long payPeriodId,
            @RequestParam(required = false) Long employeeId) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");

        PayPeriod payPeriod = payPeriodRepository.findById(payPeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayPeriod not found with payPeriodId: " + payPeriodId));

        List<Employee> employees = employeeId != null
                ? List.of(employeeRepository.findById(employeeId)
                        .orElseThrow(() -> new ResourceNotFoundException("Employee not found with employeeId: " + employeeId)))
                : employeeRepository.findAllSorted();

        Map<Long, Float> payrollByEmployee = payrollSummaryRepository.findByPayPeriodId(payPeriodId).stream()
                .filter(p -> p.getEmployeeId() != null && p.getHours() != null)
                .collect(Collectors.groupingBy(PayrollSummary::getEmployeeId, Collectors.summingDouble(PayrollSummary::getHours)))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().floatValue()));

        Map<Long, Float> timesheetByEmployee = timeSheetRepository
                .findByWorkDateBetweenOrderByWorkDate(payPeriod.getStartDate(), payPeriod.getEndDate()).stream()
                .filter(t -> t.getEmployeeId() != null && t.getHours() != null)
                .collect(Collectors.groupingBy(TimeSheet::getEmployeeId, Collectors.summingDouble(TimeSheet::getHours)))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().floatValue()));

        Map<Long, Float> invoicedByEmployee = new HashMap<>();
        for (Bills bill : billsRepository.findOverlappingRange(payPeriod.getStartDate(), payPeriod.getEndDate())) {
            if (bill.getEmployeeId() == null) continue;
            long billDays = ChronoUnit.DAYS.between(bill.getStartDate(), bill.getEndDate()) + 1;
            if (billDays <= 0) continue;
            LocalDate overlapStart = bill.getStartDate().isAfter(payPeriod.getStartDate()) ? bill.getStartDate() : payPeriod.getStartDate();
            LocalDate overlapEnd = bill.getEndDate().isBefore(payPeriod.getEndDate()) ? bill.getEndDate() : payPeriod.getEndDate();
            long overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
            if (overlapDays <= 0) continue;
            float proratedHours = bill.getHours() * ((float) overlapDays / billDays);
            invoicedByEmployee.merge(bill.getEmployeeId(), proratedHours, Float::sum);
        }

        return employees.stream().map(employee -> {
            EmployeeHoursReportRow row = new EmployeeHoursReportRow();
            row.setEmployeeId(employee.getEmployeeId());
            row.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
            row.setPayPeriodId(payPeriodId);
            row.setPayPeriodStartDate(payPeriod.getStartDate());
            row.setPayPeriodEndDate(payPeriod.getEndDate());

            float payrollHours = payrollByEmployee.getOrDefault(employee.getEmployeeId(), 0f);
            float timesheetHours = timesheetByEmployee.getOrDefault(employee.getEmployeeId(), 0f);
            float invoicedHours = invoicedByEmployee.getOrDefault(employee.getEmployeeId(), 0f);

            row.setPayrollHours(payrollHours);
            row.setTimesheetHours(timesheetHours);
            row.setInvoicedHours(invoicedHours);
            row.setInvoicedHoursEstimated(true);

            // Invoiced hours are a prorated estimate (see class comment) — too
            // imprecise to flag a "discrepancy" against. Only Payroll vs
            // Timesheet are both exact, independently-recorded figures, so
            // that's the only pair this check compares.
            String detail = "";
            if (Math.abs(payrollHours - timesheetHours) > DISCREPANCY_TOLERANCE_HOURS) {
                detail = String.format("Payroll vs Timesheet differs by %.1f hrs.", Math.abs(payrollHours - timesheetHours));
            }
            row.setHasDiscrepancy(!detail.isEmpty());
            row.setDiscrepancyDetail(detail);

            return row;
        }).collect(Collectors.toList());
    }
}
