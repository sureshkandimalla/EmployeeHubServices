package com.employeehub.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvoiceHoursDiscrepancy {
    private Long invoiceId;
    private Long invoiceNumber;
    private Long projectId;
    private float invoiceHours;
    private float billedHours;
    private float difference;
}
