package com.employeehub;

import com.employeehub.exception.InvoiceException;
import com.employeehub.model.Assignment;
import com.employeehub.model.Bills;
import com.employeehub.model.Invoice;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.BillsRepository;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.service.InvoiceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Invoice creation is the money path: it decides whether a save creates a new
 * invoice or updates an existing one (keyed on project + exact period start
 * date), and it keeps the bills billed against that invoice in lockstep.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private BillsRepository billsRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    // ── fixtures ────────────────────────────────────────────────────────────

    private com.employeehub.domain.Invoice domainInvoice(long projectId, LocalDate start, LocalDate end,
                                                         Long invoiceNumber, float hours, float total) {
        com.employeehub.domain.Invoice invoice = new com.employeehub.domain.Invoice();
        invoice.setProjectId(projectId);
        invoice.setStartDate(start);
        invoice.setEndDate(end);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setHours(hours);
        invoice.setTotal(total);
        invoice.setBillRate(50f);
        invoice.setInvoicePaidAmount(0f);
        return invoice;
    }

    private Invoice entityInvoice(Long id, long projectId, LocalDate start, LocalDate end,
                                  Long invoiceNumber, float hours, float total, String status) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setProjectId(projectId);
        invoice.setStartDate(start);
        invoice.setEndDate(end);
        invoice.setInvoiceMonth(start);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setHours(hours);
        invoice.setTotal(total);
        invoice.setStatus(status);
        return invoice;
    }

    private Bills bill(long billId, Long invoiceId, String status, float hours, float billing,
                       LocalDate start, LocalDate end) {
        Bills b = new Bills();
        b.setBillId(billId);
        b.setInvoiceId(invoiceId);
        b.setStatus(status);
        b.setHours(hours);
        b.setBilling(billing);
        b.setStartDate(start);
        b.setEndDate(end);
        return b;
    }

    private Assignment assignment(long id, long projectId, long employeeId, float wage,
                                  LocalDate start, LocalDate end, String type) {
        Assignment a = new Assignment();
        a.setAssignmentId(id);
        a.setProjectId(projectId);
        a.setEmployeeId(employeeId);
        a.setWage(wage);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setAssignmentType(type);
        return a;
    }

    private void echoInvoiceSaveWithId(long assignedId) {
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(assignedId);
            return saved;
        });
    }

    // ── createInvoiceObject: creating a brand new invoice ───────────────────

    @Test
    @DisplayName("createInvoiceObject (new): persists a new invoice when no invoice exists for that project and period")
    void createsNewInvoice() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(7L, LocalDate.of(2026, 6, 1)))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(assignmentRepository.findByProjectId(7L)).thenReturn(Collections.emptyList());
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        assertNotNull(result);
        assertEquals(160f, result.getHours());
        assertEquals(8000f, result.getTotal());
        assertEquals(501L, result.getInvoiceNumber());
    }

    @Test
    @DisplayName("createInvoiceObject (new): a new invoice starts with status Created")
    void newInvoiceStatusIsCreated() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(assignmentRepository.findByProjectId(anyLong())).thenReturn(Collections.emptyList());
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        assertEquals("Created", result.getStatus());
    }

    @Test
    @DisplayName("createInvoiceObject (new): invoiceMonth on a new invoice is its period start date, so weekly periods stay distinct")
    void newInvoiceMonthIsStartDate() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(assignmentRepository.findByProjectId(anyLong())).thenReturn(Collections.emptyList());
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), 502L, 40f, 2000f), null);

        assertEquals(LocalDate.of(2026, 6, 8), result.getInvoiceMonth());
    }

    @Test
    @DisplayName("createInvoiceObject (new): a new invoice is dated today")
    void newInvoiceIsDatedToday() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(assignmentRepository.findByProjectId(anyLong())).thenReturn(Collections.emptyList());
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        assertEquals(LocalDate.now(), result.getInvoiceDate());
    }

    @Test
    @DisplayName("createInvoiceObject (new): the bill rate from the request is stored as the invoice's billing rate")
    void newInvoiceCarriesBillRate() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(assignmentRepository.findByProjectId(anyLong())).thenReturn(Collections.emptyList());
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        assertEquals(50f, result.getBilling());
    }

    @Test
    @DisplayName("createInvoiceObject (new): wraps a repository failure in InvoiceException")
    void wrapsSaveFailure() {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        when(invoiceRepository.save(any(Invoice.class))).thenThrow(new RuntimeException("db down"));

        InvoiceException ex = assertThrows(InvoiceException.class, () -> invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null));

        assertEquals("Failed to save invoice", ex.getMessage());
    }

    // ── createInvoiceObject: updating the existing invoice for a period ─────

    @Test
    @DisplayName("createInvoiceObject (existing): updates in place instead of inserting a second row for the same period")
    void updatesExistingRatherThanInserting() throws Exception {
        Invoice existing = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 160f, 8000f, "Created");
        when(invoiceRepository.findByProjectIdAndStartDate(7L, LocalDate.of(2026, 6, 1)))
                .thenReturn(List.of(existing));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 170f, 8500f), null);

        assertEquals(100L, result.getId());
        assertEquals(170f, result.getHours());
        assertEquals(8500f, result.getTotal());
    }

    @Test
    @DisplayName("createInvoiceObject (existing): a changed invoice number alone is enough to trigger the update")
    void invoiceNumberChangeTriggersUpdate() throws Exception {
        Invoice existing = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 160f, 8000f, "Created");
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any())).thenReturn(List.of(existing));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(anyLong())).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 777L, 160f, 8000f), null);

        assertEquals(777L, result.getInvoiceNumber());
    }

    @Test
    @DisplayName("createInvoiceObject (existing): picks the oldest row when duplicate rows already exist for one period")
    void picksOldestDuplicate() throws Exception {
        Invoice older = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 160f, 8000f, "Created");
        Invoice newer = entityInvoice(250L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                502L, 160f, 8000f, "Created");
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any())).thenReturn(List.of(newer, older));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(anyLong())).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 180f, 9000f), null);

        assertEquals(100L, result.getId());
    }

    @Test
    @DisplayName("createInvoiceObject (existing): a different period start date for the same project creates a new invoice, not an update")
    void differentPeriodIsANewInvoice() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(7L, LocalDate.of(2026, 6, 8)))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(101L);
        when(billsRepository.findByInvoiceId(101L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), 502L, 40f, 2000f), null);

        assertEquals(101L, result.getId());
    }

    @Test
    @DisplayName("createInvoiceObject (existing): createInvoiceObject: persists an End Date change on an otherwise unchanged invoice")
    void endDateOnlyChangeIsPersisted() throws Exception {
        Invoice existing = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 160f, 8000f, "Created");
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any())).thenReturn(List.of(existing));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(anyLong())).thenReturn(Collections.emptyList());

        Invoice result = invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), 501L, 160f, 8000f), null);

        assertNotNull(result, "an End Date edit must still be saved");
        assertEquals(LocalDate.of(2026, 6, 25), result.getEndDate());
    }

    @Test
    @DisplayName("createInvoiceObject (existing): a save that changes nothing at all does not write to the database")
    void noOpSaveDoesNotWrite() throws Exception {
        Invoice existing = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 160f, 8000f, "Created");
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any())).thenReturn(List.of(existing));

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(invoiceRepository, never()).save(any());
    }

    // ── bill creation / sync on save ────────────────────────────────────────

    @Test
    @DisplayName("bill sync: creates one bill per assignment active during the invoice's month")
    void createsOneBillPerActiveAssignment() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(List.of(
                assignment(1L, 7L, 11L, 45f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "EMP_PAY"),
                assignment(2L, 7L, 12L, 10f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "COMM")));

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(billsRepository, times(2)).save(any(Bills.class));
    }

    @Test
    @DisplayName("bill sync: skips an assignment that ended before the invoice's month")
    void skipsAssignmentEndedBeforeInvoiceMonth() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(List.of(
                assignment(1L, 7L, 11L, 45f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31), "EMP_PAY")));

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(billsRepository, never()).save(any(Bills.class));
    }

    @Test
    @DisplayName("bill sync: skips an assignment that starts after the invoice's month")
    void skipsAssignmentStartingAfterInvoiceMonth() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(List.of(
                assignment(1L, 7L, 11L, 45f, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "EMP_PAY")));

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(billsRepository, never()).save(any(Bills.class));
    }

    @Test
    @DisplayName("bill sync: skips an assignment with no dates at all rather than failing the save")
    void skipsAssignmentWithoutDates() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(List.of(
                assignment(1L, 7L, 11L, 45f, null, null, "EMP_PAY")));

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(billsRepository, never()).save(any(Bills.class));
    }

    @Test
    @DisplayName("bill sync: a created bill copies the invoice's hours and dates and the assignment's wage")
    void createdBillMirrorsInvoiceAndAssignment() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(List.of(
                assignment(55L, 7L, 11L, 45f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "EMP_PAY")));
        ArgumentCaptor<Bills> captor = ArgumentCaptor.forClass(Bills.class);

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(billsRepository).save(captor.capture());
        Bills created = captor.getValue();
        assertEquals(100L, created.getInvoiceId());
        assertEquals(7L, created.getProjectId());
        assertEquals(55L, created.getAssignmentId());
        assertEquals(11L, created.getEmployeeId());
        assertEquals(160f, created.getHours());
        assertEquals(45f, created.getBilling());
        assertEquals(LocalDate.of(2026, 6, 1), created.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 30), created.getEndDate());
    }

    @Test
    @DisplayName("bill sync: a created bill's total is hours times the assignment's wage")
    void createdBillTotalIsHoursTimesWage() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(List.of(
                assignment(55L, 7L, 11L, 45f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "EMP_PAY")));
        ArgumentCaptor<Bills> captor = ArgumentCaptor.forClass(Bills.class);

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(billsRepository).save(captor.capture());
        assertEquals(160f * 45f, captor.getValue().getTotal());
    }

    @Test
    @DisplayName("bill sync: a created bill starts at status Created with nothing paid out")
    void createdBillStartsUnpaid() throws Exception {
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        echoInvoiceSaveWithId(100L);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findByProjectId(7L)).thenReturn(List.of(
                assignment(55L, 7L, 11L, 45f, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "EMP_PAY")));
        ArgumentCaptor<Bills> captor = ArgumentCaptor.forClass(Bills.class);

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 160f, 8000f), null);

        verify(billsRepository).save(captor.capture());
        assertEquals("Created", captor.getValue().getStatus());
        assertEquals(0f, captor.getValue().getBillPaidAmount());
    }

    @Test
    @DisplayName("bill sync: existing bills are updated in place, never duplicated, when the invoice's hours change")
    void existingBillsAreUpdatedNotDuplicated() throws Exception {
        Invoice existing = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 160f, 8000f, "Created");
        when(invoiceRepository.findByProjectIdAndStartDate(anyLong(), any())).thenReturn(List.of(existing));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        Bills existingBill = bill(9L, 100L, "Created", 160f, 45f,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(existingBill));

        invoiceService.createInvoiceObject(
                domainInvoice(7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 170f, 8500f), null);

        assertEquals(170f, existingBill.getHours());
        assertEquals(170f * 45f, existingBill.getTotal());
        verify(assignmentRepository, never()).findByProjectId(anyLong());
    }

    // ── applyInvoiceHoursToBills / syncBillsHoursForInvoice ─────────────────

    @Test
    @DisplayName("applyInvoiceHoursToBills: pushes the invoice's hours onto every bill and recomputes each total")
    void applyInvoiceHoursToBills_updatesHoursAndTotals() {
        Invoice invoice = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 120f, 6000f, "Created");
        Bills a = bill(1L, 100L, "Created", 160f, 45f, null, null);
        Bills b = bill(2L, 100L, "Created", 160f, 10f, null, null);

        invoiceService.applyInvoiceHoursToBills(invoice, List.of(a, b));

        assertEquals(120f, a.getHours());
        assertEquals(120f * 45f, a.getTotal());
        assertEquals(120f, b.getHours());
        assertEquals(120f * 10f, b.getTotal());
        verify(billsRepository, times(2)).save(any(Bills.class));
    }

    @Test
    @DisplayName("applyInvoiceHoursToBills: does nothing when the invoice has no bills")
    void applyInvoiceHoursToBills_noBills() {
        Invoice invoice = entityInvoice(100L, 7L, null, null, 501L, 120f, 6000f, "Created");

        invoiceService.applyInvoiceHoursToBills(invoice, Collections.emptyList());

        verify(billsRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncBillsHoursForInvoice: looks the bills up by invoice id, then applies the hours")
    void syncBillsHoursForInvoice_looksUpByInvoiceId() {
        Invoice invoice = entityInvoice(100L, 7L, null, null, 501L, 80f, 4000f, "Created");
        Bills existing = bill(1L, 100L, "Created", 160f, 50f, null, null);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(existing));

        invoiceService.syncBillsHoursForInvoice(invoice);

        assertEquals(80f, existing.getHours());
        assertEquals(4000f, existing.getTotal());
    }

    // ── markBillsInvoiceCleared ─────────────────────────────────────────────

    @Test
    @DisplayName("markBillsInvoiceCleared: moves every bill on the invoice to Invoice Cleared")
    void markBillsInvoiceCleared_updatesEveryBill() {
        Invoice invoice = entityInvoice(100L, 7L, null, null, 501L, 80f, 4000f, "Paid");
        Bills a = bill(1L, 100L, "Created", 80f, 50f, null, null);
        Bills b = bill(2L, 100L, "Created", 80f, 10f, null, null);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(a, b));

        invoiceService.markBillsInvoiceCleared(invoice);

        assertEquals("Invoice Cleared", a.getStatus());
        assertEquals("Invoice Cleared", b.getStatus());
    }

    // ── validateBillStatusForAllInvoices ───────────────────────────────────

    @Test
    @DisplayName("validateBillStatusForAllInvoices: a Paid invoice's Created bills become Invoice Cleared")
    void validateBillStatus_clearsBillsOfPaidInvoice() {
        Invoice paid = entityInvoice(100L, 7L, null, null, 501L, 80f, 4000f, "Paid");
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(paid));
        Bills stale = bill(1L, 100L, "Created", 80f, 50f, null, null);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(stale));

        Map<String, Integer> result = invoiceService.validateBillStatusForAllInvoices();

        assertEquals("Invoice Cleared", stale.getStatus());
        assertEquals(1, result.get("invoicesChecked"));
        assertEquals(1, result.get("billsUpdated"));
    }

    @Test
    @DisplayName("validateBillStatusForAllInvoices: an unpaid invoice's stale bill is corrected back to Created")
    void validateBillStatus_resetsBillsOfUnpaidInvoice() {
        Invoice unpaid = entityInvoice(100L, 7L, null, null, 501L, 80f, 4000f, "Created");
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(unpaid));
        Bills legacy = bill(1L, 100L, "Active", 80f, 50f, null, null);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(legacy));

        Map<String, Integer> result = invoiceService.validateBillStatusForAllInvoices();

        assertEquals("Created", legacy.getStatus());
        assertEquals(1, result.get("billsUpdated"));
    }

    @Test
    @DisplayName("validateBillStatusForAllInvoices: a bill already Paid is never downgraded")
    void validateBillStatus_neverDowngradesAPaidBill() {
        Invoice paid = entityInvoice(100L, 7L, null, null, 501L, 80f, 4000f, "Paid");
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(paid));
        Bills paidBill = bill(1L, 100L, "Paid", 80f, 50f, null, null);
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(paidBill));

        Map<String, Integer> result = invoiceService.validateBillStatusForAllInvoices();

        assertEquals("Paid", paidBill.getStatus());
        assertEquals(0, result.get("billsUpdated"));
        verify(billsRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateBillStatusForAllInvoices: an already-correct bill is left untouched (re-runnable)")
    void validateBillStatus_isIdempotent() {
        Invoice paid = entityInvoice(100L, 7L, null, null, 501L, 80f, 4000f, "paid");
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(paid));
        when(billsRepository.findByInvoiceId(100L))
                .thenReturn(List.of(bill(1L, 100L, "Invoice Cleared", 80f, 50f, null, null)));

        Map<String, Integer> result = invoiceService.validateBillStatusForAllInvoices();

        assertEquals(0, result.get("billsUpdated"));
    }

    @Test
    @DisplayName("validateBillStatusForAllInvoices: reports zero when there are no invoices")
    void validateBillStatus_noInvoices() {
        when(invoiceRepository.getAllInvoices()).thenReturn(Collections.emptyList());

        Map<String, Integer> result = invoiceService.validateBillStatusForAllInvoices();

        assertEquals(0, result.get("invoicesChecked"));
        assertEquals(0, result.get("billsUpdated"));
    }

    // ── validateBillDatesForAllInvoices ────────────────────────────────────

    @Test
    @DisplayName("validateBillDatesForAllInvoices: realigns a bill whose end date drifted from its invoice's")
    void validateBillDates_fixesMismatchedEndDate() {
        Invoice invoice = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 80f, 4000f, "Created");
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(invoice));
        Bills drifted = bill(1L, 100L, "Created", 80f, 50f,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(drifted));

        Map<String, Integer> result = invoiceService.validateBillDatesForAllInvoices();

        assertEquals(LocalDate.of(2026, 6, 30), drifted.getEndDate());
        assertEquals(1, result.get("billsUpdated"));
    }

    @Test
    @DisplayName("validateBillDatesForAllInvoices: realigns a bill whose start date drifted")
    void validateBillDates_fixesMismatchedStartDate() {
        Invoice invoice = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 80f, 4000f, "Created");
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(invoice));
        Bills drifted = bill(1L, 100L, "Created", 80f, 50f,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(drifted));

        invoiceService.validateBillDatesForAllInvoices();

        assertEquals(LocalDate.of(2026, 6, 1), drifted.getStartDate());
    }

    @Test
    @DisplayName("validateBillDatesForAllInvoices: an already-aligned bill is not rewritten (re-runnable)")
    void validateBillDates_isIdempotent() {
        Invoice invoice = entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 80f, 4000f, "Created");
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(invoice));
        when(billsRepository.findByInvoiceId(100L)).thenReturn(List.of(
                bill(1L, 100L, "Created", 80f, 50f, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))));

        Map<String, Integer> result = invoiceService.validateBillDatesForAllInvoices();

        assertEquals(0, result.get("billsUpdated"));
        verify(billsRepository, never()).save(any());
    }

    // ── deleteOrphanedBills ─────────────────────────────────────────────────

    @Test
    @DisplayName("deleteOrphanedBills: a dry run reports the orphans but deletes nothing")
    void deleteOrphanedBills_dryRunDeletesNothing() {
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(
                entityInvoice(100L, 7L, null, null, 501L, 1f, 1f, "Created")));
        when(billsRepository.findAll()).thenReturn(List.of(
                bill(1L, 100L, "Created", 1f, 1f, null, null),
                bill(2L, 999L, "Created", 1f, 1f, null, null)));

        Map<String, Object> result = invoiceService.deleteOrphanedBills(true);

        assertEquals(2, result.get("billsChecked"));
        assertEquals(1, result.get("orphanedFound"));
        assertEquals(false, result.get("deleted"));
        assertEquals(List.of(2L), result.get("orphanedBillIds"));
        verify(billsRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("deleteOrphanedBills: a real run deletes exactly the orphaned bills")
    void deleteOrphanedBills_deletesOrphans() {
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(
                entityInvoice(100L, 7L, null, null, 501L, 1f, 1f, "Created")));
        Bills orphan = bill(2L, 999L, "Created", 1f, 1f, null, null);
        when(billsRepository.findAll()).thenReturn(List.of(
                bill(1L, 100L, "Created", 1f, 1f, null, null), orphan));

        Map<String, Object> result = invoiceService.deleteOrphanedBills(false);

        assertEquals(true, result.get("deleted"));
        verify(billsRepository).deleteAll(List.of(orphan));
    }

    @Test
    @DisplayName("deleteOrphanedBills: a bill with no invoice id at all counts as orphaned")
    void deleteOrphanedBills_treatsNullInvoiceIdAsOrphan() {
        when(invoiceRepository.getAllInvoices()).thenReturn(Collections.emptyList());
        when(billsRepository.findAll()).thenReturn(List.of(bill(3L, null, "Created", 1f, 1f, null, null)));

        Map<String, Object> result = invoiceService.deleteOrphanedBills(true);

        assertEquals(1, result.get("orphanedFound"));
    }

    @Test
    @DisplayName("deleteOrphanedBills: finds nothing on a second run (idempotent)")
    void deleteOrphanedBills_findsNothingWhenAllValid() {
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(
                entityInvoice(100L, 7L, null, null, 501L, 1f, 1f, "Created")));
        when(billsRepository.findAll()).thenReturn(List.of(bill(1L, 100L, "Created", 1f, 1f, null, null)));

        Map<String, Object> result = invoiceService.deleteOrphanedBills(false);

        assertEquals(0, result.get("orphanedFound"));
    }

    // ── backfillBillsForExistingInvoices ───────────────────────────────────

    @Test
    @DisplayName("backfillBillsForExistingInvoices: bills only the invoices that have none yet")
    void backfill_skipsAlreadyBilledInvoices() throws Exception {
        when(billsRepository.findDistinctInvoiceIdsWithBills()).thenReturn(List.of(100L));
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(
                entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 80f, 4000f, "Created"),
                entityInvoice(101L, 7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 502L, 80f, 4000f, "Created")));
        when(assignmentRepository.findByProjectId(7L)).thenReturn(Collections.emptyList());

        assertEquals(1, invoiceService.backfillBillsForExistingInvoices());
    }

    @Test
    @DisplayName("backfillBillsForExistingInvoices: skips an invoice with no project or no invoice month")
    void backfill_skipsIncompleteInvoices() throws Exception {
        when(billsRepository.findDistinctInvoiceIdsWithBills()).thenReturn(Collections.emptyList());
        Invoice noProject = entityInvoice(100L, 0L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                501L, 80f, 4000f, "Created");
        noProject.setProjectId(null);
        Invoice noMonth = entityInvoice(101L, 7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                502L, 80f, 4000f, "Created");
        noMonth.setInvoiceMonth(null);
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(noProject, noMonth));

        assertEquals(0, invoiceService.backfillBillsForExistingInvoices());
        verify(billsRepository, never()).save(any());
    }

    @Test
    @DisplayName("backfillBillsForExistingInvoices: returns zero when every invoice is already billed")
    void backfill_returnsZeroWhenNothingToDo() throws Exception {
        when(billsRepository.findDistinctInvoiceIdsWithBills()).thenReturn(List.of(100L));
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(
                entityInvoice(100L, 7L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 501L, 80f, 4000f, "Created")));

        assertEquals(0, invoiceService.backfillBillsForExistingInvoices());
    }

    // ── lookups ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getInvoiceByMonthAndProjectId: converts the ISO date to the repository's yyyyMM key")
    void getInvoiceByMonthAndProjectId_formatsMonthKey() {
        when(invoiceRepository.findByInvoiceByMonthAndProjectId("202606", 7L))
                .thenReturn(List.of(entityInvoice(100L, 7L, null, null, 501L, 1f, 1f, "Created")));

        assertEquals(1, invoiceService.getInvoiceByMonthAndProjectId("2026-06-15", 7L).size());
    }

    @Test
    @DisplayName("getInvoiceByMonthAndProjectId: rejects a date that is not ISO formatted")
    void getInvoiceByMonthAndProjectId_rejectsBadDate() {
        assertThrows(java.time.format.DateTimeParseException.class,
                () -> invoiceService.getInvoiceByMonthAndProjectId("06/15/2026", 7L));
    }

    @Test
    @DisplayName("getInvoiceByProjectId: returns every invoice for the project")
    void getInvoiceByProjectId_delegates() {
        when(invoiceRepository.findByProjectId(7L)).thenReturn(List.of(
                entityInvoice(100L, 7L, null, null, 501L, 1f, 1f, "Created")));

        assertEquals(1, invoiceService.getInvoiceByProjectId(7L).size());
    }

    // ── isValid ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isValid: accepts a real identifier")
    void isValid_acceptsPositive() {
        assertTrue(invoiceService.isValid(42L));
    }

    @Test
    @DisplayName("isValid: rejects null, zero and Long.MIN_VALUE placeholders")
    void isValid_rejectsPlaceholders() {
        assertFalse(invoiceService.isValid(null));
        assertFalse(invoiceService.isValid(0L));
        assertFalse(invoiceService.isValid(Long.MIN_VALUE));
    }
}
