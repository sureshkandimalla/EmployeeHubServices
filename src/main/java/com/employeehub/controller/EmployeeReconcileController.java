package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.domain.EmployeeReconcileRecord;
import com.employeehub.domain.ReconcileExpense;
import com.employeehub.domain.ReconcileRecord;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.*;
import com.employeehub.repository.*;
import com.employeehub.service.EmployeeReconcileRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reconcile")
public class EmployeeReconcileController {

	private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

	@Autowired private EmployeeRepository employeeRepository;
	@Autowired private InvoiceRepository invoiceRepository;
	@Autowired private PayrollRepository payrollRepository;
	@Autowired private AdjustmentRepository adjustmentRepository;

	@GetMapping("/getReconcileRecords/{employeeId}")
	public List<ReconcileRecord> getReconcileRecordsForEmployee(@PathVariable Long employeeId) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		// validate employee exists, then delegate
		employeeRepository.findById(employeeId)
				.orElseThrow(() -> new ResourceNotFoundException("Employee not found with employeeId: " + employeeId));
		return getReconcileRecords(employeeId);
	}

	@GetMapping("/getReconcileRecords")
	public List<EmployeeReconcileRecord> getReconcileRecords() {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		return employeeRepository.findAllSorted().stream()
				.map(employee -> {
					List<ReconcileRecord> records = getReconcileRecords(employee.getEmployeeId());
					long totalIncome  = (long) records.stream().mapToDouble(ReconcileRecord::getIncome).sum();
					long totalExpense = (long) records.stream().mapToDouble(ReconcileRecord::getExpense).sum();
					return EmployeeReconcileRecordMapper.mapToReconcileRecord(employee, totalIncome, totalExpense);
				})
				.collect(Collectors.toList());
	}

	private List<ReconcileRecord> getReconcileRecords(Long employeeId) {
		Employee employee = employeeRepository.findById(employeeId)
				.orElseThrow(() -> new ResourceNotFoundException("Employee not found with employeeId: " + employeeId));

		List<ReconcileRecord> reconcileRecords = new ArrayList<>();

		// ── Project / Invoice records ────────────────────────────────────────────
		employee.getProject().forEach(project -> {
			ReconcileRecord reconcileRecord = new ReconcileRecord();
			reconcileRecord.setDescription("Consulting services provided for " + project.getCustomer().getCustomerCompanyName());

			project.getBillRates().forEach(wage -> {
				if (wage.getStartDate() == null || wage.getEndDate() == null) return;
				String startDate = wage.getStartDate().format(YEAR_MONTH_FMT);
				String endDate   = wage.getEndDate().format(YEAR_MONTH_FMT);
				List<Invoice> invoices = invoiceRepository.findAllInvoicesForTheMonthProject(startDate, endDate, project.getProjectId());

				reconcileRecord.setWage(wage.getWage());
				reconcileRecord.setProjectBilling(wage.getWage());
				reconcileRecord.setHours((float) invoices.stream().mapToDouble(Invoice::getHours).sum());
				reconcileRecord.setInvoiceTotal((float) invoices.stream().mapToDouble(Invoice::getTotal).sum());
				reconcileRecord.setInvoicePaidAmount((float) invoices.stream().mapToDouble(Invoice::getInvoicePaidAmount).sum());
				reconcileRecord.setStartDate(wage.getStartDate());
				reconcileRecord.setEndDate(wage.getEndDate());
			});

			project.getAssignments().stream()
					.filter(assignment -> employeeId.equals(assignment.getEmployeeId()))
					.filter(assignment -> assignment.getStartDate() != null && assignment.getEndDate() != null)
					.forEach(assignment -> {
						String startDate = assignment.getStartDate().format(YEAR_MONTH_FMT);
						String endDate   = assignment.getEndDate().format(YEAR_MONTH_FMT);
						List<Invoice> invoices = invoiceRepository.findAllInvoicesForTheMonthProject(startDate, endDate, assignment.getProjectId());

						ReconcileExpense reconcileExpense = new ReconcileExpense();
						reconcileExpense.setExpenseType(assignment.getAssignmentType());
						reconcileExpense.setHours((float) invoices.stream().mapToDouble(Invoice::getHours).sum());
						reconcileExpense.setWage(assignment.getWage());
						reconcileExpense.setEmployeeId(assignment.getEmployeeId());
						reconcileExpense.setDescription(assignment.getDescription()
								+ " for " + project.getProjectName()
								+ " @ $ " + assignment.getWage());
						reconcileExpense.setTotal(reconcileExpense.getHours() * reconcileExpense.getWage());
						reconcileExpense.setStatus(assignment.getStatus());
						reconcileExpense.setStartDate(assignment.getStartDate());
						reconcileExpense.setEndDate(assignment.getEndDate());

						reconcileRecord.setIncome(reconcileRecord.getIncome() + reconcileExpense.getTotal());
						if (reconcileRecord.getExpenseRecords() == null)
							reconcileRecord.setExpenseRecords(new ArrayList<>());
						reconcileRecord.getExpenseRecords().add(reconcileExpense);
					});

			reconcileRecords.add(reconcileRecord);
		});

		// ── Payroll records grouped by year ──────────────────────────────────────
		Map<Integer, List<Payroll>> payrollByYear = payrollRepository
				.findPayrollsWithPayPeriodDatesByEmployeeId(employeeId)
				.stream()
				.filter(p -> p.getCheckDate() != null)
				.collect(Collectors.groupingBy(p -> p.getCheckDate().getYear()));

		payrollByYear.forEach((year, payrolls) -> {
			ReconcileRecord reconcileRecord = new ReconcileRecord();
			reconcileRecord.setDescription("Payroll for " + employee.getFirstName() + " " + employee.getLastName() + " for year " + year);
			reconcileRecord.setHours((float) payrolls.stream().mapToDouble(Payroll::getHours).sum());
			reconcileRecord.setExpense((float) payrolls.stream().mapToDouble(Payroll::getTotalPaid).sum());
			reconcileRecord.setStartDate(payrolls.get(0).getPayCycleStartDate());
			reconcileRecord.setEndDate(payrolls.get(payrolls.size() - 1).getPayCycleEndDate());
			reconcileRecords.add(reconcileRecord);
		});

		// ── Adjustment records ───────────────────────────────────────────────────
		adjustmentRepository.findAdjustmentsByEmployeeId(employeeId).forEach(row -> {
			com.employeehub.domain.Adjustment adjustment = new com.employeehub.domain.Adjustment(row);
			ReconcileRecord reconcileRecord = new ReconcileRecord();
			reconcileRecord.setDescription(adjustment.getAdjustmentType());
			reconcileRecord.setStartDate(adjustment.getAdjustmentDate());
			if (employeeId.equals(adjustment.getToId()))   // fix: use .equals() not ==
				reconcileRecord.setExpense((float) adjustment.getAmount());
			else
				reconcileRecord.setIncome((float) adjustment.getAmount());
			reconcileRecords.add(reconcileRecord);
		});

		return reconcileRecords;
	}
}
