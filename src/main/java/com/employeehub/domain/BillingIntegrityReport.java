package com.employeehub.domain;

import com.employeehub.model.Bills;
import com.employeehub.model.Invoice;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BillingIntegrityReport {
    private List<Bills> billsWithoutInvoice;
    private List<Invoice> invoicesWithoutBills;
    private List<InvoiceHoursDiscrepancy> hoursDiscrepancies;
}
