package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.domain.BillingIntegrityReport;
import com.employeehub.domain.InvoiceHoursDiscrepancy;
import com.employeehub.model.Bills;
import com.employeehub.model.Invoice;
import com.employeehub.repository.BillsRepository;
import com.employeehub.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Data-integrity check across Bills/Invoice — a bill is supposed to be
// created for, and stay in sync with, exactly one invoice (see
// InvoiceService's bill-sync logic), so this surfaces the three ways that
// sync can drift: a bill with no (or a dangling) invoiceId, an invoice with
// no bill at all, and an invoice/bill pair whose hours disagree.
@RestController
@RequestMapping("/api/v1/reports")
public class BillingIntegrityReportController {

    // Floats accumulate rounding noise across many bills — treat anything
    // under a hundredth of an hour as "no real discrepancy".
    private static final float HOURS_TOLERANCE = 0.01f;

    @Autowired
    private BillsRepository billsRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;

    @GetMapping("/billing-integrity")
    public BillingIntegrityReport getBillingIntegrityReport() {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");

        List<Bills> allBills = billsRepository.findAll();
        List<Invoice> allInvoices = invoiceRepository.findAll();

        Set<Long> invoiceIds = allInvoices.stream().map(Invoice::getId).collect(Collectors.toSet());

        List<Bills> billsWithoutInvoice = allBills.stream()
                .filter(bill -> bill.getInvoiceId() == null || !invoiceIds.contains(bill.getInvoiceId()))
                .collect(Collectors.toList());

        Map<Long, List<Bills>> billsByInvoiceId = allBills.stream()
                .filter(bill -> bill.getInvoiceId() != null)
                .collect(Collectors.groupingBy(Bills::getInvoiceId));

        List<Invoice> invoicesWithoutBills = allInvoices.stream()
                .filter(invoice -> !billsByInvoiceId.containsKey(invoice.getId()))
                .collect(Collectors.toList());

        List<InvoiceHoursDiscrepancy> hoursDiscrepancies = allInvoices.stream()
                .map(invoice -> {
                    float billedHours = billsByInvoiceId
                            .getOrDefault(invoice.getId(), Collections.emptyList())
                            .stream()
                            .map(Bills::getHours)
                            .reduce(0f, Float::sum);
                    float difference = invoice.getHours() - billedHours;
                    return new InvoiceHoursDiscrepancy(
                            invoice.getId(), invoice.getInvoiceNumber(), invoice.getProjectId(),
                            invoice.getHours(), billedHours, difference);
                })
                .filter(d -> Math.abs(d.getDifference()) > HOURS_TOLERANCE)
                .collect(Collectors.toList());

        return new BillingIntegrityReport(billsWithoutInvoice, invoicesWithoutBills, hoursDiscrepancies);
    }
}
