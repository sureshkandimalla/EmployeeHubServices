package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.BillingIntegrityReportController;
import com.employeehub.domain.BillingIntegrityReport;
import com.employeehub.model.Bills;
import com.employeehub.model.Invoice;
import com.employeehub.repository.BillsRepository;
import com.employeehub.repository.InvoiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingIntegrityReportControllerTest {

    @Mock
    private BillsRepository billsRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private BillingIntegrityReportController controller;

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private Bills bill(Long billId, Long invoiceId, float hours) {
        Bills bill = new Bills();
        bill.setBillId(billId);
        bill.setInvoiceId(invoiceId);
        bill.setHours(hours);
        return bill;
    }

    private Invoice invoice(Long id, Long invoiceNumber, float hours) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setHours(hours);
        return invoice;
    }

    @Test
    void rejectsRolesOtherThanAdminOrAccounting() {
        CurrentUserContext.set("hr@example.com", "HR");
        assertThrows(ResponseStatusException.class, controller::getBillingIntegrityReport);
    }

    @Test
    void flagsBillWithNoInvoiceAndDanglingInvoiceId() {
        CurrentUserContext.set("admin@example.com", "ADMIN");
        when(billsRepository.findAll()).thenReturn(Arrays.asList(
                bill(1L, null, 10f),      // never invoiced
                bill(2L, 99L, 5f)));       // invoiceId points at nothing
        when(invoiceRepository.findAll()).thenReturn(Collections.emptyList());

        BillingIntegrityReport report = controller.getBillingIntegrityReport();

        assertEquals(2, report.getBillsWithoutInvoice().size());
        assertTrue(report.getInvoicesWithoutBills().isEmpty());
        assertTrue(report.getHoursDiscrepancies().isEmpty());
    }

    @Test
    void flagsInvoiceWithNoBills() {
        CurrentUserContext.set("accounting@example.com", "ACCOUNTING");
        when(billsRepository.findAll()).thenReturn(Collections.emptyList());
        when(invoiceRepository.findAll()).thenReturn(List.of(invoice(1L, 1001L, 40f)));

        BillingIntegrityReport report = controller.getBillingIntegrityReport();

        assertEquals(1, report.getInvoicesWithoutBills().size());
        assertEquals(1001L, report.getInvoicesWithoutBills().get(0).getInvoiceNumber());
    }

    @Test
    void flagsHoursMismatchBetweenInvoiceAndItsBillsButNotAnExactMatch() {
        CurrentUserContext.set("admin@example.com", "ADMIN");
        when(billsRepository.findAll()).thenReturn(Arrays.asList(
                bill(1L, 1L, 20f),
                bill(2L, 1L, 15f),     // invoice 1: billed 35, invoiced 40 -> mismatch
                bill(3L, 2L, 25f)));   // invoice 2: billed 25, invoiced 25 -> matches
        when(invoiceRepository.findAll()).thenReturn(Arrays.asList(
                invoice(1L, 1001L, 40f),
                invoice(2L, 1002L, 25f)));

        BillingIntegrityReport report = controller.getBillingIntegrityReport();

        assertEquals(1, report.getHoursDiscrepancies().size());
        assertEquals(1001L, report.getHoursDiscrepancies().get(0).getInvoiceNumber());
        assertEquals(5f, report.getHoursDiscrepancies().get(0).getDifference(), 0.001f);
    }
}
