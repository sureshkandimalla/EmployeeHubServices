package com.employeehub.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;

@Setter
@Getter
public class Bills {


  private Long BillId;

  private Long invoiceId;
  // The invoice's own human-readable number (Invoice.invoiceNumber) — not
  // set by the model.Bills constructor below since a bill only stores the
  // invoice's id; the controller resolves and sets this separately via a
  // batch invoice lookup.
  private Long invoiceNumber;
  private Long assignmentId;
  private LocalDate invoiceMonth;
  private float billing; //assignment wage
  private Long employeeId; //assignment
    private String employeeName;

  private float hours;
  private float total;
  private float billPaidAmount;
  private LocalDate billDate;
  private LocalDate startDate;
  private LocalDate endDate;
  private LocalDate paymentDate;
  private String status;
  private String billType;

  public Bills(com.employeehub.model.Bills modelBill) {
      this.BillId = modelBill.getBillId();
      this.invoiceId = modelBill.getInvoiceId();
      this.assignmentId = modelBill.getAssignmentId();
      this.invoiceMonth = modelBill.getInvoiceMonth();
      this.billing = modelBill.getBilling();
      this.employeeId = modelBill.getEmployeeId();
      this.hours = modelBill.getHours();
      this.total = modelBill.getTotal();
      this.billPaidAmount = modelBill.getBillPaidAmount();
      this.billDate = modelBill.getBillDate();
      this.startDate = modelBill.getStartDate();
      this.endDate = modelBill.getEndDate();
      this.paymentDate = modelBill.getPaymentDate();
      this.status = modelBill.getStatus();
      this.billType = modelBill.getBillType();
  }

@Override
public String toString() {
	return "Bills [BillId=" + BillId + ", invoiceId=" + invoiceId + ", assignmentId=" + assignmentId + ", invoiceMonth="
			+ invoiceMonth + ", billing=" + billing + ", employeeId=" + employeeId + ", hours=" + hours + ", total="
			+ total + ", billPaidAmount=" + billPaidAmount + ", billDate=" + billDate + ", startDate=" + startDate
			+ ", endDate=" + endDate + ", paymentDate=" + paymentDate + ", status=" + status + "]";
}
  
  
}
