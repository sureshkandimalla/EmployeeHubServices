package com.employeehub;

import com.employeehub.exception.BillsException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Bills;
import com.employeehub.repository.BillsRepository;
import com.employeehub.service.BillsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bills are the money paid out against an invoice, so the paid/unpaid amount
 * reconciliation in updateBill is the highest-risk behaviour here.
 */
@ExtendWith(MockitoExtension.class)
class BillsServiceTest {

    @Mock
    private BillsRepository billsRepository;

    @InjectMocks
    private BillsService billsService;

    private Bills bill(long id, String status, float total, float paidAmount) {
        Bills b = new Bills();
        b.setBillId(id);
        b.setStatus(status);
        b.setTotal(total);
        b.setBillPaidAmount(paidAmount);
        return b;
    }

    // ── getBillById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBillById: returns the bill when it exists")
    void getBillById_found() {
        when(billsRepository.findById(1L)).thenReturn(Optional.of(bill(1L, "Created", 100f, 0f)));

        assertEquals(1L, billsService.getBillById(1L).getBillId());
    }

    @Test
    @DisplayName("getBillById: throws ResourceNotFoundException (404) for an unknown id")
    void getBillById_notFound() {
        when(billsRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> billsService.getBillById(999L));

        assertTrue(ex.getMessage().contains("999"));
    }

    // ── createBill ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createBill: persists and returns the new bill")
    void createBill_persists() throws Exception {
        Bills input = bill(0L, "Created", 100f, 0f);
        when(billsRepository.save(input)).thenReturn(input);

        assertSame(input, billsService.createBill(input));
        verify(billsRepository).save(input);
    }

    @Test
    @DisplayName("createBill: wraps a repository failure in BillsException")
    void createBill_wrapsFailure() {
        Bills input = bill(0L, "Created", 100f, 0f);
        when(billsRepository.save(input)).thenThrow(new RuntimeException("db down"));

        BillsException ex = assertThrows(BillsException.class, () -> billsService.createBill(input));

        assertEquals("Bill data not saved", ex.getMessage());
    }

    // ── updateBill ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateBill: marking a bill Paid with no amount entered pays out its full total")
    void updateBill_paidDefaultsPaidAmountToTotal() throws Exception {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Created", 0f, 0f)));
        when(billsRepository.saveAndFlush(any(Bills.class))).thenAnswer(inv -> inv.getArgument(0));

        Bills updated = billsService.updateBill(5L, bill(0L, "Paid", 1200f, 0f));

        assertEquals(1200f, updated.getBillPaidAmount());
    }

    @Test
    @DisplayName("updateBill: a part payment already entered on a Paid bill is left alone")
    void updateBill_paidKeepsExplicitPaidAmount() throws Exception {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Created", 0f, 0f)));
        when(billsRepository.saveAndFlush(any(Bills.class))).thenAnswer(inv -> inv.getArgument(0));

        Bills updated = billsService.updateBill(5L, bill(0L, "Paid", 1200f, 500f));

        assertEquals(500f, updated.getBillPaidAmount());
    }

    @Test
    @DisplayName("updateBill: moving a bill back to Unpaid clears any paid amount")
    void updateBill_unpaidClearsPaidAmount() throws Exception {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Paid", 1200f, 1200f)));
        when(billsRepository.saveAndFlush(any(Bills.class))).thenAnswer(inv -> inv.getArgument(0));

        Bills updated = billsService.updateBill(5L, bill(0L, "Unpaid", 1200f, 1200f));

        assertEquals(0f, updated.getBillPaidAmount());
    }

    @Test
    @DisplayName("updateBill: status matching ignores case, so 'PAID' also pays out the full total")
    void updateBill_statusMatchIsCaseInsensitive() throws Exception {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Created", 0f, 0f)));
        when(billsRepository.saveAndFlush(any(Bills.class))).thenAnswer(inv -> inv.getArgument(0));

        Bills updated = billsService.updateBill(5L, bill(0L, "PAID", 900f, 0f));

        assertEquals(900f, updated.getBillPaidAmount());
    }

    @Test
    @DisplayName("updateBill: any other status leaves the paid amount exactly as submitted")
    void updateBill_otherStatusLeavesPaidAmountAlone() throws Exception {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Created", 0f, 0f)));
        when(billsRepository.saveAndFlush(any(Bills.class))).thenAnswer(inv -> inv.getArgument(0));

        Bills updated = billsService.updateBill(5L, bill(0L, "Invoice Cleared", 900f, 123f));

        assertEquals(123f, updated.getBillPaidAmount());
    }

    @Test
    @DisplayName("updateBill: a null status is tolerated and the paid amount is left untouched")
    void updateBill_nullStatusIsTolerated() throws Exception {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Created", 0f, 0f)));
        when(billsRepository.saveAndFlush(any(Bills.class))).thenAnswer(inv -> inv.getArgument(0));

        Bills updated = billsService.updateBill(5L, bill(0L, null, 900f, 42f));

        assertEquals(42f, updated.getBillPaidAmount());
    }

    @Test
    @DisplayName("updateBill: the path id wins over any billId in the request body")
    void updateBill_pathIdWinsOverBodyId() throws Exception {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Created", 0f, 0f)));
        ArgumentCaptor<Bills> captor = ArgumentCaptor.forClass(Bills.class);
        when(billsRepository.saveAndFlush(any(Bills.class))).thenAnswer(inv -> inv.getArgument(0));

        billsService.updateBill(5L, bill(999L, "Created", 100f, 0f));

        verify(billsRepository).saveAndFlush(captor.capture());
        assertEquals(5L, captor.getValue().getBillId());
    }

    @Test
    @DisplayName("updateBill: throws ResourceNotFoundException (404) for an unknown id")
    void updateBill_notFound() {
        when(billsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> billsService.updateBill(999L, bill(0L, "Paid", 100f, 0f)));
        verify(billsRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("updateBill: wraps a repository failure in BillsException")
    void updateBill_wrapsFailure() {
        when(billsRepository.findById(5L)).thenReturn(Optional.of(bill(5L, "Created", 0f, 0f)));
        when(billsRepository.saveAndFlush(any(Bills.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(BillsException.class, () -> billsService.updateBill(5L, bill(0L, "Created", 1f, 0f)));
    }

    // ── deleteBill ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteBill: deletes the bill that was looked up")
    void deleteBill_deletesExisting() {
        Bills existing = bill(5L, "Created", 100f, 0f);
        when(billsRepository.findById(5L)).thenReturn(Optional.of(existing));

        billsService.deleteBill(5L);

        verify(billsRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteBill: throws ResourceNotFoundException (404) and deletes nothing for an unknown id")
    void deleteBill_notFound() {
        when(billsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> billsService.deleteBill(999L));
        verify(billsRepository, never()).delete(any());
    }

    // ── finders ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findBillsByInvoiceMonth: passes the yyyyMM key straight through to the repository")
    void findBillsByInvoiceMonth_delegates() {
        List<Bills> bills = List.of(bill(1L, "Created", 100f, 0f));
        when(billsRepository.findBillsByInvoiceMonth("202606")).thenReturn(Optional.of(bills));

        assertEquals(bills, billsService.findBillsByInvoiceMonth("202606").orElseThrow());
    }

    @Test
    @DisplayName("findBillsByInvoiceMonth: returns an empty Optional when the month has no bills")
    void findBillsByInvoiceMonth_empty() {
        when(billsRepository.findBillsByInvoiceMonth("202601")).thenReturn(Optional.empty());

        assertTrue(billsService.findBillsByInvoiceMonth("202601").isEmpty());
    }

    @Test
    @DisplayName("findBillsForProject: returns the project's bills")
    void findBillsForProject_delegates() {
        when(billsRepository.findBillsForProject(7L)).thenReturn(Optional.of(Collections.emptyList()));

        assertTrue(billsService.findBillsForProject(7L).orElseThrow().isEmpty());
    }

    @Test
    @DisplayName("findBillsForEmployee: returns the employee's bills")
    void findBillsForEmployee_delegates() {
        List<Bills> bills = List.of(bill(3L, "Paid", 500f, 500f));
        when(billsRepository.findByEmployeeId(42L)).thenReturn(Optional.of(bills));

        assertEquals(bills, billsService.findBillsForEmployee(42L).orElseThrow());
    }
}
