package com.employeehub;

import com.employeehub.model.Employee;
import com.employeehub.model.Invoice;
import com.employeehub.model.Project;
import com.employeehub.model.Wage;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.repository.ProjectRepository;
import com.employeehub.repository.TimeSheetRepository;
import com.employeehub.service.TimeSheetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Covers TimeSheetService's three read-aggregation methods — all pure
 * grouping/bucketing logic, with no DB of their own to assert against:
 *   - getTimesheetSummaries(): one row per (employee, assignment, month)
 *     across every timesheet ever entered — backs the Timesheets grid.
 *   - getMissingTimesheets(): one row per (employee, assignment, month)
 *     NOT fully Approved, including months with zero entries — backs the
 *     Unsubmitted Timesheets report.
 *   - getTimesheetsByInvoiceFrequency(): one row per (project, work order,
 *     invoice period), bucketed by the project's own invoice term instead
 *     of a calendar month, compared against any matching Invoice — backs
 *     the "By Invoice Frequency" tab on the Timesheets page. Driven by
 *     Project + its Wage (work order) rows, not Assignment — see the
 *     method's own comment for why.
 * Dates are fixed in 2020 rather than relative to "today" so these periods
 * always land safely in the past no matter when the suite runs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeSheetServiceTest {

    @Mock
    private TimeSheetRepository timeSheetRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private TimeSheetService timeSheetService;

    private Employee employee(long employeeId, String firstName, String lastName, String email) {
        Employee e = new Employee();
        e.setEmployeeId(employeeId);
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setEmailId(email);
        return e;
    }

    private Wage workOrder(long projectId, LocalDate start, LocalDate end) {
        Wage w = new Wage();
        w.setProjectId(projectId);
        w.setWageType("Billing");
        w.setStartDate(start);
        w.setEndDate(end);
        return w;
    }

    private Project project(long projectId, String projectName, Employee emp, String invoiceTerm,
            String weekStartDay, Wage... workOrders) {
        Project p = new Project();
        p.setProjectId(projectId);
        p.setProjectName(projectName);
        p.setEmployee(emp);
        p.setInvoiceTerm(invoiceTerm);
        p.setWeekStartDay(weekStartDay);
        p.setBillRates(List.of(workOrders));
        return p;
    }

    private Object[] timesheetRow(long projectId, String projectName, long assignmentId, long employeeId,
            LocalDate workDate, float hours, String status) {
        return new Object[]{"Jane", "Doe", projectId, projectName, assignmentId, employeeId,
                Date.valueOf(workDate), hours, status, Date.valueOf(workDate), "jane@example.com"};
    }

    // findAllAssignmentsWithDetails() row shape, still used by
    // getMissingTimesheets() (unchanged — only getTimesheetsByInvoiceFrequency
    // moved off Assignment).
    private Object[] assignmentDetailsRow(String firstName, String lastName, long projectId, String projectName,
            long assignmentId, long employeeId, LocalDate start, LocalDate end, String email) {
        return new Object[]{firstName, lastName, projectId, projectName, assignmentId, employeeId,
                Date.valueOf(start), end == null ? null : Date.valueOf(end), email};
    }

    private Invoice invoice(long id, long projectId, LocalDate startDate, LocalDate endDate, float hours, String status) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber(9000L + id);
        invoice.setProjectId(projectId);
        invoice.setStartDate(startDate);
        invoice.setEndDate(endDate);
        invoice.setInvoiceMonth(startDate);
        invoice.setHours(hours);
        invoice.setStatus(status);
        return invoice;
    }

    @Test
    @DisplayName("Monthly project: one row per month, matched invoice vs. not-yet-invoiced month")
    void monthlyProject_bucketsByCalendarMonthAndFlagsMissingInvoice() {
        Employee emp = employee(100, "Jane", "Doe", "jane@example.com");
        Wage wo = workOrder(10, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 28));
        when(projectRepository.findAll()).thenReturn(List.of(
                project(10, "ProjA", emp, "3", "MONDAY", wo)));

        when(timeSheetRepository.findAllWithNames()).thenReturn(List.<Object[]>of(
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2020, 1, 5), 10f, "Submitted"),
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2020, 1, 20), 5f, "Approved"),
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2020, 2, 10), 8f, "Submitted")));

        when(invoiceRepository.findAll()).thenReturn(List.of(
                invoice(500, 10, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31), 15f, "Created")));

        List<Map<String, Object>> rows = timeSheetService.getTimesheetsByInvoiceFrequency();
        assertEquals(2, rows.size());

        Map<String, Object> jan = rows.stream().filter(r -> "2020-01-01".equals(r.get("periodStart"))).findFirst().orElseThrow();
        assertEquals("Monthly", jan.get("invoiceFrequency"));
        assertEquals(100L, jan.get("employeeId"));
        assertEquals("2020-01", jan.get("yearMonth"));
        assertEquals(15f, (Float) jan.get("timesheetHours"));
        assertEquals(15f, (Float) jan.get("invoicedHours"));
        assertEquals(0f, (Float) jan.get("variance"));
        assertEquals("Matched", jan.get("comparisonStatus"));
        assertEquals(500L, jan.get("invoiceId"));

        Map<String, Object> feb = rows.stream().filter(r -> "2020-02-01".equals(r.get("periodStart"))).findFirst().orElseThrow();
        assertEquals(8f, (Float) feb.get("timesheetHours"));
        assertEquals(0f, (Float) feb.get("invoicedHours"));
        assertNull(feb.get("invoiceId"));
        assertEquals("No Invoice Yet", feb.get("comparisonStatus"));
    }

    @Test
    @DisplayName("Weekly project: hours mismatch between timesheet and invoice is flagged")
    void weeklyProject_flagsHoursMismatch() {
        Employee emp = employee(101, "John", "Smith", "john@example.com");
        Wage wo = workOrder(11, LocalDate.of(2020, 1, 6), LocalDate.of(2020, 1, 12));
        when(projectRepository.findAll()).thenReturn(List.of(
                project(11, "ProjB", emp, "1", "MONDAY", wo)));

        when(timeSheetRepository.findAllWithNames()).thenReturn(List.<Object[]>of(
                timesheetRow(11, "ProjB", 2, 101, LocalDate.of(2020, 1, 7), 20f, "Approved")));

        when(invoiceRepository.findAll()).thenReturn(List.of(
                invoice(600, 11, LocalDate.of(2020, 1, 6), LocalDate.of(2020, 1, 12), 15f, "Created")));

        List<Map<String, Object>> rows = timeSheetService.getTimesheetsByInvoiceFrequency();
        assertEquals(1, rows.size());

        Map<String, Object> row = rows.get(0);
        assertEquals("Weekly", row.get("invoiceFrequency"));
        assertEquals("2020-01-06", row.get("periodStart"));
        assertEquals("2020-01-12", row.get("periodEnd"));
        assertEquals(20f, (Float) row.get("timesheetHours"));
        assertEquals(15f, (Float) row.get("invoicedHours"));
        assertEquals(5f, (Float) row.get("variance"));
        assertEquals("Hours Mismatch", row.get("comparisonStatus"));
    }

    @Test
    @DisplayName("Draft-only hours never count toward timesheetHours, only toward the status")
    void draftHours_areExcludedFromConfirmedTotal() {
        Employee emp = employee(100, "Jane", "Doe", "jane@example.com");
        Wage wo = workOrder(10, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));
        when(projectRepository.findAll()).thenReturn(List.of(
                project(10, "ProjA", emp, "3", "MONDAY", wo)));

        when(timeSheetRepository.findAllWithNames()).thenReturn(List.<Object[]>of(
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2020, 1, 5), 6f, "Draft")));

        when(invoiceRepository.findAll()).thenReturn(List.of());

        List<Map<String, Object>> rows = timeSheetService.getTimesheetsByInvoiceFrequency();
        assertEquals(1, rows.size());
        assertEquals(0f, (Float) rows.get(0).get("timesheetHours"));
        assertEquals("Draft", rows.get(0).get("timesheetStatus"));
        // Zero confirmed hours and no invoice — nothing to flag yet.
        assertEquals("Matched", rows.get(0).get("comparisonStatus"));
    }

    @Test
    @DisplayName("A project's real timesheet hours produce exactly one row per period, even with a concurrent side-pay Assignment on the same project")
    void sidePayAssignmentOnSameProject_doesNotProduceAGhostRow() {
        // Reproduces the real bug: an employee has a normal EMP_PAY
        // assignment AND a second, concurrent EMP_REF (referral) assignment
        // on the same project. Since this method is now driven by
        // Project/Wage, not Assignment, the second assignment's existence
        // (or absence — it's deliberately not mocked anywhere here) has no
        // bearing on the result at all.
        Employee emp = employee(102, "Ram", "K", "ram@example.com");
        Wage wo = workOrder(12, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));
        when(projectRepository.findAll()).thenReturn(List.of(
                project(12, "ProjC", emp, "3", "MONDAY", wo)));

        when(timeSheetRepository.findAllWithNames()).thenReturn(List.<Object[]>of(
                timesheetRow(12, "ProjC", 27, 102, LocalDate.of(2020, 1, 10), 176f, "Approved")));

        when(invoiceRepository.findAll()).thenReturn(List.of(
                invoice(700, 12, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31), 176f, "Created")));

        List<Map<String, Object>> rows = timeSheetService.getTimesheetsByInvoiceFrequency();
        assertEquals(1, rows.size(), "exactly one row for January, not two");
        assertEquals("Matched", rows.get(0).get("comparisonStatus"));
        assertEquals(176f, (Float) rows.get(0).get("timesheetHours"));
    }

    @Test
    @DisplayName("getTimesheetSummaries: groups by assignment+month, sums hours, most-restrictive status wins")
    void getTimesheetSummaries_groupsByAssignmentAndMonth() {
        when(timeSheetRepository.findAllWithNames()).thenReturn(List.<Object[]>of(
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2026, 1, 5), 4f, "Draft"),
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2026, 1, 10), 4f, "Submitted"),
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2026, 2, 1), 8f, "Approved")));

        List<Map<String, Object>> rows = timeSheetService.getTimesheetSummaries();
        assertEquals(2, rows.size());

        Map<String, Object> jan = rows.stream().filter(r -> "2026-01".equals(r.get("yearMonth"))).findFirst().orElseThrow();
        assertEquals(8f, (Float) jan.get("totalHours")); // 4 + 4, regardless of status
        assertEquals("Submitted", jan.get("status")); // Submitted outranks Draft
        assertEquals("Jane Doe", jan.get("employeeName"));
        assertEquals("ProjA", jan.get("projectName"));

        Map<String, Object> feb = rows.stream().filter(r -> "2026-02".equals(r.get("yearMonth"))).findFirst().orElseThrow();
        assertEquals(8f, (Float) feb.get("totalHours"));
        assertEquals("Approved", feb.get("status"));
    }

    @Test
    @DisplayName("getMissingTimesheets: skips Approved months, flags Submitted and zero-entry months")
    void getMissingTimesheets_flagsEveryNonApprovedMonthInTheAssignmentWindow() {
        when(assignmentRepository.findAllAssignmentsWithDetails()).thenReturn(List.<Object[]>of(
                assignmentDetailsRow("Jane", "Doe", 10, "ProjA", 1, 100,
                        LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 31), "jane@example.com")));

        when(timeSheetRepository.findAllWithNames()).thenReturn(List.<Object[]>of(
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2020, 1, 15), 8f, "Approved"),
                timesheetRow(10, "ProjA", 1, 100, LocalDate.of(2020, 2, 15), 8f, "Submitted")));
        // March has zero timesheet rows at all.

        List<Map<String, Object>> rows = timeSheetService.getMissingTimesheets();

        assertTrue(rows.stream().noneMatch(r -> "2020-01".equals(r.get("yearMonth"))), "Approved month must not be flagged");

        Map<String, Object> feb = rows.stream().filter(r -> "2020-02".equals(r.get("yearMonth"))).findFirst().orElseThrow();
        assertEquals("Submitted", feb.get("status"));

        Map<String, Object> mar = rows.stream().filter(r -> "2020-03".equals(r.get("yearMonth"))).findFirst().orElseThrow();
        assertEquals("No Timesheet", mar.get("status"));

        assertEquals(2, rows.size());
    }
}
