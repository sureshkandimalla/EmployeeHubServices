package com.employeehub;

import com.employeehub.model.Invoice;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.InvoiceRepository;
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
 * getTimesheetsByInvoiceFrequency backs the "By Invoice Frequency" tab on
 * the Timesheets page — one row per (employee, assignment, invoice period),
 * bucketed by the project's own invoice term instead of a calendar month,
 * compared against any matching Invoice. Dates are fixed in 2020 rather than
 * relative to "today" so these periods always land safely in the past no
 * matter when the suite runs.
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

    @InjectMocks
    private TimeSheetService timeSheetService;

    private Object[] assignmentRow(String firstName, String lastName, long projectId, String projectName,
            long assignmentId, long employeeId, LocalDate start, LocalDate end, String email,
            String invoiceTerm, String weekStartDay) {
        return new Object[]{firstName, lastName, projectId, projectName, assignmentId, employeeId,
                Date.valueOf(start), end == null ? null : Date.valueOf(end), email, invoiceTerm, weekStartDay};
    }

    private Object[] timesheetRow(long projectId, String projectName, long assignmentId, long employeeId,
            LocalDate workDate, float hours, String status) {
        return new Object[]{"Jane", "Doe", projectId, projectName, assignmentId, employeeId,
                Date.valueOf(workDate), hours, status, Date.valueOf(workDate), "jane@example.com"};
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
        when(assignmentRepository.findAllAssignmentsWithInvoiceTerm()).thenReturn(List.<Object[]>of(
                assignmentRow("Jane", "Doe", 10, "ProjA", 1, 100,
                        LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 28), "jane@example.com", "3", "MONDAY")));

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
        assertEquals(1L, jan.get("assignmentId"));
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
        when(assignmentRepository.findAllAssignmentsWithInvoiceTerm()).thenReturn(List.<Object[]>of(
                assignmentRow("John", "Smith", 11, "ProjB", 2, 101,
                        LocalDate.of(2020, 1, 6), LocalDate.of(2020, 1, 12), "john@example.com", "1", "MONDAY")));

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
        when(assignmentRepository.findAllAssignmentsWithInvoiceTerm()).thenReturn(List.<Object[]>of(
                assignmentRow("Jane", "Doe", 10, "ProjA", 1, 100,
                        LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31), "jane@example.com", "3", "MONDAY")));

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
}
