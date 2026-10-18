package com.employeehub;

import com.employeehub.model.Assignment;
import com.employeehub.model.Customer;
import com.employeehub.model.Employee;
import com.employeehub.model.Invoice;
import com.employeehub.model.Project;
import com.employeehub.model.Wage;
import com.employeehub.repository.CustomerRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.ProjectRepository;
import com.employeehub.service.InvoiceService;
import com.employeehub.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ProjectService owns the two calculations the business actually reads off the
 * Projects screen — the 8% employer tax (with its OPT/C2C exemptions) and the
 * net margin — plus the invoice-period breakdown that drives Generate
 * Invoice. All three are pure date/number logic and are pinned here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private ProjectService projectService;

    // ── fixtures ────────────────────────────────────────────────────────────

    private Employee employee(long id, String employeeType, String visa) {
        Employee e = new Employee();
        e.setEmployeeId(id);
        e.setFirstName("Ada");
        e.setLastName("Lovelace");
        e.setCompanyName("Intellan Technologies LLC");
        e.setEmployeeType(employeeType);
        e.setVisa(visa);
        return e;
    }

    private Customer customer(long id) {
        Customer c = new Customer();
        c.setCustomerId(id);
        c.setCustomerCompanyName("Acme Corp");
        return c;
    }

    private Assignment assignment(String type, String taxType, float wage, String status, LocalDate start) {
        Assignment a = new Assignment();
        a.setAssignmentType(type);
        a.setAssignmentTaxType(taxType);
        a.setWage(wage);
        a.setStatus(status);
        a.setStartDate(start);
        a.setEmployeeId(11L);
        return a;
    }

    private Project project(String invoiceTerm, String weekStartDay, String status,
                            LocalDate start, LocalDate end, List<Assignment> assignments) {
        Project p = new Project();
        p.setProjectId(7L);
        p.setProjectName("Acme Platform");
        p.setClient("Acme");
        p.setInvoiceTerm(invoiceTerm);
        p.setPaymentTerm("Net 30");
        p.setWeekStartDay(weekStartDay);
        p.setStatus(status);
        p.setStartDate(start);
        p.setEndDate(end);
        p.setEmployee(employee(11L, "W2", "H1B"));
        p.setCustomer(customer(21L));
        p.setAssignments(assignments == null ? new ArrayList<>() : assignments);
        return p;
    }

    private Wage wage(float rate, LocalDate start, LocalDate end) {
        Wage w = new Wage();
        w.setWageId(301L);
        w.setWage(rate);
        w.setStartDate(start);
        w.setEndDate(end);
        return w;
    }

    private Invoice invoice(long id, LocalDate start, LocalDate invoiceMonth, float hours, float total) {
        Invoice i = new Invoice();
        i.setId(id);
        i.setProjectId(7L);
        i.setStartDate(start);
        i.setInvoiceMonth(invoiceMonth);
        i.setHours(hours);
        i.setTotal(total);
        i.setInvoiceNumber(901L);
        return i;
    }

    // ── createProject: header mapping ───────────────────────────────────────

    @Test
    @DisplayName("createProject: maps the project, employee, customer and work-order fields onto the row")
    void createProject_mapsHeaderFields() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new ArrayList<>());
        Wage w = wage(100f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        com.employeehub.domain.Project row = projectService.createProject(project, w, null);

        assertEquals(7L, row.getProjectId());
        assertEquals(301L, row.getWageId());
        assertEquals("Acme Platform", row.getProjectName());
        assertEquals("Acme", row.getClientName());
        assertEquals("Net 30", row.getPaymentTerm());
        assertEquals(11L, row.getEmployeeId());
        assertEquals("Ada Lovelace", row.getEmployeeName());
        assertEquals("Intellan Technologies LLC", row.getCompanyName());
        assertEquals(21L, row.getCustomerId());
        assertEquals("Acme Corp", row.getCustomerName());
        assertEquals(100f, row.getBillRate());
    }

    @Test
    @DisplayName("createProject: the row's dates come from the work order, not the project")
    void createProject_datesComeFromWorkOrder() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new ArrayList<>());
        Wage w = wage(100f, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 31));

        com.employeehub.domain.Project row = projectService.createProject(project, w, null);

        assertEquals(LocalDate.of(2026, 3, 1), row.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), row.getEndDate());
    }

    // ── createProject: employer tax ─────────────────────────────────────────

    @Test
    @DisplayName("createProject: employer tax on a W2 employee is 8% of their pay")
    void createProject_employerTaxIsEightPercentForW2() {
        Assignment empPay = assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(empPay));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(50f, row.getEmployeePay());
        assertEquals(4f, row.getEmployerTax(), 0.0001);
    }

    @Test
    @DisplayName("createProject: employer tax is also booked as the internal expense")
    void createProject_employerTaxIsAlsoInternalExpense() {
        Assignment empPay = assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(empPay));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(row.getEmployerTax(), row.getExpenseInternal());
    }

    @Test
    @DisplayName("createProject: an OPT visa holder is exempt from employer tax")
    void createProject_optVisaIsExemptFromEmployerTax() {
        Assignment empPay = assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(empPay));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "OPT EAD")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(0f, row.getEmployerTax());
    }

    @Test
    @DisplayName("createProject: a C2C employee is exempt from employer tax even if the assignment says W2")
    void createProject_c2cEmployeeIsExemptEvenWhenAssignmentSaysW2() {
        Assignment empPay = assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(empPay));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "C2C", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(0f, row.getEmployerTax());
        assertEquals(0f, row.getExpenseInternal());
    }

    @Test
    @DisplayName("createProject: the C2C check ignores case")
    void createProject_c2cCheckIgnoresCase() {
        Assignment empPay = assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(empPay));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "c2c", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(0f, row.getEmployerTax());
    }

    @Test
    @DisplayName("createProject: a non-W2 assignment tax type attracts no employer tax")
    void createProject_nonW2AssignmentHasNoEmployerTax() {
        Assignment empPay = assignment("EMP_PAY", "1099", 50f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(empPay));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "1099", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(0f, row.getEmployerTax());
    }

    @Test
    @DisplayName("createProject: zero employee pay produces zero employer tax")
    void createProject_zeroPayMeansZeroTax() {
        Assignment empPay = assignment("EMP_PAY", "W2", 0f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(empPay));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(0f, row.getEmployerTax());
    }

    @Test
    @DisplayName("createProject: with no EMP_PAY assignment there is no employee pay or employer tax")
    void createProject_noEmpPayAssignment() {
        Assignment commission = assignment("COMM", "W2", 10f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(commission));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(0f, row.getEmployeePay());
        assertEquals(0f, row.getEmployerTax());
    }

    @Test
    @DisplayName("createProject: prefers the Active EMP_PAY assignment over an inactive one")
    void createProject_prefersActiveEmpPayAssignment() {
        Assignment inactive = assignment("EMP_PAY", "W2", 80f, "Inactive", LocalDate.of(2026, 6, 1));
        Assignment active = assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(inactive, active));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(50f, row.getEmployeePay());
    }

    @Test
    @DisplayName("createProject: with no Active EMP_PAY assignment, falls back to the latest-starting one")
    void createProject_fallsBackToLatestEmpPayAssignment() {
        Assignment older = assignment("EMP_PAY", "W2", 40f, "Inactive", LocalDate.of(2026, 1, 1));
        Assignment newer = assignment("EMP_PAY", "W2", 60f, "Inactive", LocalDate.of(2026, 6, 1));
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), List.of(older, newer));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(60f, row.getEmployeePay());
    }

    // ── createProject: expenses and net ─────────────────────────────────────

    @Test
    @DisplayName("createProject: external expense is the sum of every non-EMP_PAY assignment wage")
    void createProject_externalExpenseSumsNonEmpPayAssignments() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of(assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1)),
                        assignment("COMM", "W2", 5f, "Active", LocalDate.of(2026, 1, 1)),
                        assignment("REF", "W2", 3f, "Active", LocalDate.of(2026, 1, 1))));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertEquals(8f, row.getExpenseExternal(), 0.0001);
    }

    @Test
    @DisplayName("createProject: net is bill rate minus employee pay, external expense and internal expense")
    void createProject_netSubtractsEveryCost() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of(assignment("EMP_PAY", "W2", 50f, "Active", LocalDate.of(2026, 1, 1)),
                        assignment("COMM", "W2", 5f, "Active", LocalDate.of(2026, 1, 1))));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        // 100 bill - 50 pay - 5 commission - 4 employer tax
        assertEquals(41f, row.getNet(), 0.0001);
    }

    @Test
    @DisplayName("createProject: net can go negative when the costs exceed the bill rate")
    void createProject_netCanBeNegative() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of(assignment("EMP_PAY", "W2", 90f, "Active", LocalDate.of(2026, 1, 1)),
                        assignment("COMM", "W2", 30f, "Active", LocalDate.of(2026, 1, 1))));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        assertTrue(row.getNet() < 0, "net should report the loss rather than clamping at zero");
    }

    // ── createProject: status ───────────────────────────────────────────────

    @Test
    @DisplayName("createProject: a manually-set status always wins over the date-derived one")
    void createProject_manualStatusWins() {
        Project project = project("3", null, "Hold",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), new ArrayList<>());

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)), null);

        assertEquals("Hold", row.getStatus());
    }

    @Test
    @DisplayName("createProject: a blank status falls through to the date-derived status")
    void createProject_blankStatusFallsThrough() {
        Project project = project("3", null, "   ",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), new ArrayList<>());

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)), null);

        assertEquals("Inactive", row.getStatus());
    }

    @Test
    @DisplayName("createProject: a work order that already ended derives status Inactive")
    void createProject_pastWorkOrderIsInactive() {
        Project project = project("3", null, null, null, null, new ArrayList<>());

        com.employeehub.domain.Project row = projectService.createProject(project,
                wage(100f, LocalDate.now().minusYears(2), LocalDate.now().minusDays(1)), null);

        assertEquals("Inactive", row.getStatus());
    }

    @Test
    @DisplayName("createProject: a work order still running derives status Active")
    void createProject_currentWorkOrderIsActive() {
        Project project = project("3", null, null, null, LocalDate.now().plusYears(1), new ArrayList<>());

        com.employeehub.domain.Project row = projectService.createProject(project,
                wage(100f, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)), null);

        assertEquals("Active", row.getStatus());
    }

    @Test
    @DisplayName("createProject: a project whose own end date has passed derives status Inactive")
    void createProject_pastProjectEndDateIsInactive() {
        Project project = project("3", null, null, null, LocalDate.now().minusDays(1), new ArrayList<>());

        com.employeehub.domain.Project row = projectService.createProject(project,
                wage(100f, LocalDate.now().minusMonths(1), null), null);

        assertEquals("Inactive", row.getStatus());
    }

    @Test
    @DisplayName("createProject: open-ended dates derive status Active")
    void createProject_openEndedIsActive() {
        Project project = project("3", null, null, null, null, new ArrayList<>());

        com.employeehub.domain.Project row = projectService.createProject(project,
                wage(100f, LocalDate.now().minusMonths(1), null), null);

        assertEquals("Active", row.getStatus());
    }

    // ── createProject: attaching an existing invoice for a selected month ───

    @Test
    @DisplayName("createProject: attaches the month's existing invoice hours, id, number and total")
    void createProject_attachesExistingInvoiceForSelectedMonth() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new ArrayList<>());
        when(invoiceService.getInvoiceByMonthAndProjectId("2026-06-01", 7L))
                .thenReturn(List.of(invoice(500L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), 160f, 16000f)));

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), "2026-06-01");

        assertEquals(160f, row.getHours());
        assertEquals(500L, row.getInvoiceId());
        assertEquals(901L, row.getInvoiceNumber());
        assertEquals(16000f, row.getTotal());
    }

    @Test
    @DisplayName("createProject: leaves the invoice fields empty when the month has no invoice yet")
    void createProject_noInvoiceForSelectedMonth() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new ArrayList<>());
        when(invoiceService.getInvoiceByMonthAndProjectId("2026-06-01", 7L)).thenReturn(Collections.emptyList());

        com.employeehub.domain.Project row = projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), "2026-06-01");

        assertEquals(0f, row.getHours());
        assertEquals(0L, row.getInvoiceId());
    }

    @Test
    @DisplayName("createProject: never looks up invoices when no month was selected")
    void createProject_skipsInvoiceLookupWithoutSelectedMonth() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new ArrayList<>());

        projectService.createProject(project, wage(100f,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), null);

        verify(invoiceService, never()).getInvoiceByMonthAndProjectId(any(), anyLong());
    }

    // ── createProjectForInvoice: period breakdown per invoice term ──────────

    @Test
    @DisplayName("createProjectForInvoice: Monthly produces one row per calendar month, first to last day")
    void createProjectForInvoice_monthlyRows() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 20)));

        assertEquals(3, rows.size());
        assertEquals(LocalDate.of(2026, 1, 1), rows.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 1, 31), rows.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 2, 1), rows.get(1).getStartDate());
        assertEquals(LocalDate.of(2026, 2, 28), rows.get(1).getEndDate());
        assertEquals(LocalDate.of(2026, 3, 31), rows.get(2).getEndDate());
    }

    @Test
    @DisplayName("createProjectForInvoice: an unrecognized invoice term falls back to Monthly")
    void createProjectForInvoice_unknownTermFallsBackToMonthly() {
        Project project = project("99", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)));

        assertEquals(1, rows.size());
        assertEquals(LocalDate.of(2026, 1, 1), rows.get(0).getStartDate());
    }

    @Test
    @DisplayName("createProjectForInvoice: Weekly produces 7-day rows anchored to Monday by default")
    void createProjectForInvoice_weeklyRowsAnchorToMonday() {
        // 2026-06-01 is a Monday.
        Project project = project("1", null, "Active",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 21), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 21)));

        assertEquals(3, rows.size());
        assertEquals(LocalDate.of(2026, 6, 1), rows.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 7), rows.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 6, 8), rows.get(1).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 15), rows.get(2).getStartDate());
    }

    @Test
    @DisplayName("createProjectForInvoice: the first Weekly row clips forward to the real work-order start date")
    void createProjectForInvoice_firstWeeklyRowClipsToStartDate() {
        // 2026-06-03 is a Wednesday; the Monday grid would start 2026-06-01.
        Project project = project("1", null, "Active",
                LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 14), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 14)));

        assertEquals(LocalDate.of(2026, 6, 3), rows.get(0).getStartDate(),
                "the first period must not bill days before the engagement began");
        assertEquals(LocalDate.of(2026, 6, 7), rows.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 6, 8), rows.get(1).getStartDate(),
                "later periods stay on the regular week grid");
    }

    @Test
    @DisplayName("createProjectForInvoice: Weekly honours a project's configured week-start day")
    void createProjectForInvoice_weeklyHonoursConfiguredWeekStartDay() {
        // 2026-06-01 is a Monday, so the previous Sunday is 2026-05-31.
        Project project = project("1", "SUNDAY", "Active",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10)));

        assertEquals(LocalDate.of(2026, 6, 1), rows.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 6), rows.get(0).getEndDate(),
                "6 days on from the Sunday anchor of 2026-05-31");
        assertEquals(LocalDate.of(2026, 6, 7), rows.get(1).getStartDate());
    }

    @Test
    @DisplayName("createProjectForInvoice: an unparseable week-start day falls back to Monday rather than failing")
    void createProjectForInvoice_badWeekStartDayFallsBackToMonday() {
        Project project = project("1", "not-a-day", "Active",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)));

        assertEquals(LocalDate.of(2026, 6, 1), rows.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 7), rows.get(0).getEndDate());
    }

    @Test
    @DisplayName("createProjectForInvoice: Biweekly produces 14-day rows")
    void createProjectForInvoice_biweeklyRows() {
        Project project = project("2", null, "Active",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28)));

        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(2026, 6, 1), rows.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 14), rows.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 6, 15), rows.get(1).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 28), rows.get(1).getEndDate());
    }

    @Test
    @DisplayName("createProjectForInvoice: Once in 4 Weeks produces 28-day rows")
    void createProjectForInvoice_fourWeeklyRows() {
        Project project = project("6", null, "Active",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 26), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 26)));

        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(2026, 6, 28), rows.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 6, 29), rows.get(1).getStartDate());
        assertEquals(LocalDate.of(2026, 7, 26), rows.get(1).getEndDate());
    }

    @Test
    @DisplayName("createProjectForInvoice: Semi-Monthly produces 1st-15th and 16th-end rows per month")
    void createProjectForInvoice_semiMonthlyRows() {
        Project project = project("5", null, "Active",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));

        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(2026, 2, 1), rows.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 2, 15), rows.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 2, 16), rows.get(1).getStartDate());
        assertEquals(LocalDate.of(2026, 2, 28), rows.get(1).getEndDate(),
                "the second half must end on the month's real last day");
    }

    @Test
    @DisplayName("createProjectForInvoice: an open-ended work order only generates periods up to today")
    void createProjectForInvoice_openEndedStopsAtToday() {
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        Project project = project("3", null, "Active", start, null, new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, start, null));

        assertEquals(2, rows.size(), "last month and this month, never a future month");
    }

    @Test
    @DisplayName("createProjectForInvoice: a work order ending in the future is still cut off at today")
    void createProjectForInvoice_futureEndDateCutOffAtToday() {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        Project project = project("3", null, "Active", start, start.plusYears(1), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, start, start.plusYears(1)));

        assertEquals(1, rows.size());
    }

    @Test
    @DisplayName("createProjectForInvoice: matches an existing invoice to its Weekly period by exact start date")
    void createProjectForInvoice_matchesWeeklyInvoiceByStartDate() {
        Project project = project("1", null, "Active",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(List.of(
                invoice(500L, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8), 40f, 4000f)));

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14)));

        assertEquals(0L, rows.get(0).getInvoiceId(), "the first week has no invoice");
        assertEquals(500L, rows.get(1).getInvoiceId());
        assertEquals(40f, rows.get(1).getHours());
        assertEquals(4000f, rows.get(1).getTotal());
    }

    @Test
    @DisplayName("createProjectForInvoice: matches an existing invoice to its Monthly period by month")
    void createProjectForInvoice_matchesMonthlyInvoiceByMonth() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(List.of(
                invoice(500L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 14), 160f, 16000f)));

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)));

        assertEquals(0L, rows.get(0).getInvoiceId());
        assertEquals(500L, rows.get(1).getInvoiceId());
    }

    @Test
    @DisplayName("createProjectForInvoice: every generated row carries the shared project header")
    void createProjectForInvoice_rowsShareProjectHeader() {
        Project project = project("3", null, "Active",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)));

        for (com.employeehub.domain.Project row : rows) {
            assertEquals(7L, row.getProjectId());
            assertEquals("Acme Platform", row.getProjectName());
            assertEquals("Ada Lovelace", row.getEmployeeName());
            assertEquals(100f, row.getBillRate());
            assertEquals("MONDAY", row.getWeekStartDay());
        }
    }

    @Test
    @DisplayName("createProjectForInvoice: rows for a finished work order are marked Inactive")
    void createProjectForInvoice_finishedWorkOrderRowsAreInactive() {
        Project project = project("3", null, "Active",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31), new ArrayList<>());
        when(invoiceService.getInvoiceByProjectId(7L)).thenReturn(Collections.emptyList());

        List<com.employeehub.domain.Project> rows = projectService.createProjectForInvoice(project,
                wage(100f, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31)));

        assertEquals("Inactive", rows.get(0).getStatus());
    }

    // ── saveProject ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveProject: returns 200 with the new project id and work order id")
    void saveProject_returnsIds() {
        com.employeehub.domain.Project request = new com.employeehub.domain.Project();
        request.setEmployeeId(11L);
        request.setCustomerId(21L);
        request.setProjectName("Acme Platform");
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 12, 31));
        request.setBillRate(100f);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));
        when(customerRepository.findById(21L)).thenReturn(Optional.of(customer(21L)));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setProjectId(55L);
            return p;
        });

        ResponseEntity<Map<String, Object>> response = projectService.saveProject(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(55L, response.getBody().get("projectId"));
        assertEquals("Project data saved Succesfully", response.getBody().get("message"));
        assertTrue(response.getBody().containsKey("wageId"));
    }

    @Test
    @DisplayName("saveProject: returns 404 when the employee does not exist")
    void saveProject_notFoundForUnknownEmployee() {
        com.employeehub.domain.Project request = new com.employeehub.domain.Project();
        request.setEmployeeId(999L);
        request.setCustomerId(21L);
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());
        when(customerRepository.findById(21L)).thenReturn(Optional.of(customer(21L)));

        ResponseEntity<Map<String, Object>> response = projectService.saveProject(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Employee or Customer not found", response.getBody().get("message"));
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveProject: returns 404 when the customer does not exist")
    void saveProject_notFoundForUnknownCustomer() {
        com.employeehub.domain.Project request = new com.employeehub.domain.Project();
        request.setEmployeeId(11L);
        request.setCustomerId(999L);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = projectService.saveProject(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveProject: the initial work order uses the work-order dates when they are supplied")
    void saveProject_usesWorkOrderDates() {
        com.employeehub.domain.Project request = new com.employeehub.domain.Project();
        request.setEmployeeId(11L);
        request.setCustomerId(21L);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 12, 31));
        request.setWorkOrderStartDate(LocalDate.of(2026, 3, 1));
        request.setWorkOrderEndDate(LocalDate.of(2026, 8, 31));
        request.setBillRate(100f);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));
        when(customerRepository.findById(21L)).thenReturn(Optional.of(customer(21L)));
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        projectService.saveProject(request);

        verify(projectRepository).save(captor.capture());
        Wage saved = captor.getValue().getBillRates().get(0);
        assertEquals(LocalDate.of(2026, 3, 1), saved.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), saved.getEndDate());
    }

    @Test
    @DisplayName("saveProject: the initial work order falls back to the project's dates when work-order dates are omitted")
    void saveProject_fallsBackToProjectDates() {
        com.employeehub.domain.Project request = new com.employeehub.domain.Project();
        request.setEmployeeId(11L);
        request.setCustomerId(21L);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 12, 31));
        request.setBillRate(100f);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));
        when(customerRepository.findById(21L)).thenReturn(Optional.of(customer(21L)));
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        projectService.saveProject(request);

        verify(projectRepository).save(captor.capture());
        Wage saved = captor.getValue().getBillRates().get(0);
        assertEquals(LocalDate.of(2026, 1, 1), saved.getStartDate());
        assertEquals(LocalDate.of(2026, 12, 31), saved.getEndDate());
    }

    @Test
    @DisplayName("saveProject: the initial work order is a Billing rate carrying the project's bill rate")
    void saveProject_initialWorkOrderIsABillingRate() {
        com.employeehub.domain.Project request = new com.employeehub.domain.Project();
        request.setEmployeeId(11L);
        request.setCustomerId(21L);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setBillRate(135f);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));
        when(customerRepository.findById(21L)).thenReturn(Optional.of(customer(21L)));
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        projectService.saveProject(request);

        verify(projectRepository).save(captor.capture());
        Wage saved = captor.getValue().getBillRates().get(0);
        assertEquals("Billing", saved.getWageType());
        assertEquals(135f, saved.getWage());
        assertEquals(LocalDate.now(), saved.getCreatedDate());
    }

    @Test
    @DisplayName("saveProject: links the resolved employee and customer onto the project row")
    void saveProject_linksEmployeeAndCustomer() {
        com.employeehub.domain.Project request = new com.employeehub.domain.Project();
        request.setEmployeeId(11L);
        request.setCustomerId(21L);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setWeekStartDay("FRIDAY");
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "W2", "H1B")));
        when(customerRepository.findById(21L)).thenReturn(Optional.of(customer(21L)));
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        projectService.saveProject(request);

        verify(projectRepository).save(captor.capture());
        assertEquals(11L, captor.getValue().getEmployee().getEmployeeId());
        assertEquals(21L, captor.getValue().getCustomer().getCustomerId());
        assertEquals("FRIDAY", captor.getValue().getWeekStartDay());
        assertEquals(LocalDate.now(), captor.getValue().getLastUpdated());
    }
}
