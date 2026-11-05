package com.employeehub.model;

import java.time.LocalDate;
import java.util.Date;

import javax.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "Bills")
public class Bills {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long BillId;

  private Long invoiceId;
  private Long projectId;
  private Long assignmentId;
  private LocalDate invoiceMonth;
  private float billing; //assignment wage
  private Long employeeId; //assignment 
  private float hours;
  private float total;
  private float billPaidAmount;
  private LocalDate billDate;
  private LocalDate startDate;
  private LocalDate endDate;
  private LocalDate paymentDate;
  private String status;
  private String billType;
  private float discount;
  private String comments;
  
  
@Override
public String toString() {
	return "Bills [BillId=" + BillId + ", invoiceId=" + invoiceId + ", projectId=" + projectId + ", assignmentId=" + assignmentId + ", invoiceMonth="
			+ invoiceMonth + ", billing=" + billing + ", employeeId=" + employeeId + ", hours=" + hours + ", total="
			+ total + ", billPaidAmount=" + billPaidAmount + ", billDate=" + billDate + ", startDate=" + startDate
			+ ", endDate=" + endDate + ", paymentDate=" + paymentDate + ", status=" + status + "]";
}
  
  
}
