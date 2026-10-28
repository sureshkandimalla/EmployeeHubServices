package com.employeehub.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
@Getter@Setter
public class Project implements Cloneable {
    private long projectId;
    private long wageId;
    private String projectName;
    private long employeeId;
    private String employeeName;
    private String employeeEmail;
    // The employee's own company (e.g. "Intellan Technologies LLC", "Code9
    // LLC") — not to be confused with customerName/client, which are the
    // outside companies this project is delivered for.
    private String companyName;
    private String customerName;
    private long customerId;
    private String clientName;
    private String client; //for project table
    private long clientId;
    private LocalDate startDate;
    private LocalDate endDate;
    // A project's own [startDate, endDate] and its (first) work order's
    // dates are independently editable in Add New Project — a project can
    // run longer than any one work order, and later work orders (added via
    // WorkOrderForm) each carry their own dates too. Falls back to the
    // project's own dates in ProjectService#saveProject when omitted, so
    // older callers (e.g. the Excel import) keep working unchanged.
    private LocalDate workOrderStartDate;
    private LocalDate workOrderEndDate;
    private float billRate;
    private float employeePay;
    private float expenseInternal;
    private float expenseExternal;

    private float employerTax;
    private float net;
    private String status;
    private String  invoiceTerm;
    private String  paymentTerm;
    private String  weekStartDay;
    private float hours;
    // Identity/"already invoiced" signal — the invoice's surrogate id, not
    // the user-typed invoiceNumber. Truthy/non-zero means this period has
    // an invoice already.
    private long invoiceId;
    // Cosmetic/business label shown and edited in the Generate Invoice
    // grid — never used to detect whether a period is already invoiced.
    private Long invoiceNumber;
    private float Billing;
    private float total;

    @Override
    public Project clone() {
        try {
            return (Project) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }
    
	@Override
	public String toString() {
		return "Project [projectId=" + projectId + ", projectName=" + projectName + ", employeeId=" + employeeId
				+ ", employeeName=" + employeeName + ", customerName=" + customerName + ", customerId=" + customerId
				+ ", clientName=" + clientName + ", clientId=" + clientId + ", startDate=" + startDate + ", endDate="
				+ endDate + ", billRate=" + billRate + ", employeePay=" + employeePay + ", expenseInternal="
				+ expenseInternal + ", expenseExternal=" + expenseExternal + ", net=" + net + ", status=" + status
				+ ", invoiceTerm=" + invoiceTerm + ", paymentTerm=" + paymentTerm + ", hours=" + hours + ", invoiceID="
				+ invoiceId + ", Billing=" + Billing + ", total=" + total + "]";
	}
    
    
  

    
}
