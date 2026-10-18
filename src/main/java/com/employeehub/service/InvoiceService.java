package com.employeehub.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.employeehub.exception.BillsException;
import com.employeehub.exception.InvoiceException;
import com.employeehub.model.Assignment;
import com.employeehub.model.Bills;
import com.employeehub.model.Invoice;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.BillsRepository;
import com.employeehub.repository.InvoiceRepository;

@Service
public class InvoiceService {
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InvoiceService.class);
	
	@Autowired
    private InvoiceRepository invoiceRepository;
	
	@Autowired
    private AssignmentRepository assignmentRepository;
	
	@Autowired
    private BillsRepository billsRepository;
	
	@Transactional
	public Invoice createInvoiceObject(com.employeehub.domain.Invoice dbInvoice, String Date)
			throws InvoiceException, BillsException {
		// TODO Auto-generated method stub

		LocalDate today = LocalDate.now();
		// Format the date as "yyyy-MM-dd"
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		String formattedDate = today.format(formatter);
		LocalDate parsedDate = LocalDate.parse(formattedDate, formatter);

		Invoice dbInvoiceObject =null;
		Invoice invoice = new Invoice();

		logger.info("dbInvoice.getInvoiceNumber(): " + dbInvoice.getInvoiceNumber() + " startDate=" + dbInvoice.getStartDate());
		// "Does an invoice already exist" is keyed off the actual business
		// identity — one invoice per project per *exact period* (its start
		// date), never off the user-typed invoiceNumber (that's what let two
		// unrelated invoices collide and silently overwrite each other) and
		// never off calendar month alone (Weekly/Biweekly/Once-in-4-Weeks/
		// Semi-Monthly all put more than one legitimate invoice in the same
		// month — matching on month collided those with each other instead:
		// confirmed live for Chandra-Aquinas, a Weekly project, where a
		// second week's save either 500'd or silently overwrote the first
		// week's row). Pick the oldest row if duplicates already exist from
		// that bug rather than crash — same defensive intent, corrected key.
		List<Invoice> existingInvoices = invoiceRepository.findByProjectIdAndStartDate(
				dbInvoice.getProjectId(), dbInvoice.getStartDate());
		Optional<Invoice> existingInvoice = existingInvoices.stream()
				.min(java.util.Comparator.comparing(Invoice::getId));

		logger.info("existingInvoice.isPresent():: " + existingInvoice.isPresent()
				+ (existingInvoices.size() > 1 ? " (WARNING: " + existingInvoices.size() + " duplicate rows for this project+period)" : ""));
		if (existingInvoice.isPresent()) {

			Invoice existing = existingInvoice.get();
			boolean numberChanged = !java.util.Objects.equals(existing.getInvoiceNumber(), dbInvoice.getInvoiceNumber());
			boolean endDateChanged = !java.util.Objects.equals(existing.getEndDate(), dbInvoice.getEndDate());
			if (!(existing.getHours()==dbInvoice.getHours()) || !(existing.getTotal()==dbInvoice.getTotal()) || numberChanged || endDateChanged) {
				existing.setHours(dbInvoice.getHours());
				existing.setTotal(dbInvoice.getTotal());
				existing.setInvoiceNumber(dbInvoice.getInvoiceNumber());
				existing.setEndDate(dbInvoice.getEndDate());
				existing.setInvoiceMonth(dbInvoice.getStartDate());
				// toadd params if required invoice_date,invoice paid amount, need to be
				// updated?

				try {
					dbInvoiceObject = invoiceRepository.save(existing); // update if existing project+period
				} catch (Exception e) {
					throw new InvoiceException("Failed to save invoice", e);
				}

			}

		} else {

			invoice.setHours(dbInvoice.getHours());
			invoice.setInvoiceNumber(dbInvoice.getInvoiceNumber());
			invoice.setTotal(dbInvoice.getTotal());
			invoice.setInvoiceDate(parsedDate);
			invoice.setStartDate(dbInvoice.getStartDate());
			invoice.setEndDate(dbInvoice.getEndDate());
			invoice.setStatus("Created"); // tochange as per business
			invoice.setPaymentDate(parsedDate);
			invoice.setProjectId(dbInvoice.getProjectId());
			invoice.setBilling(dbInvoice.getBillRate());
			invoice.setInvoicePaidAmount(dbInvoice.getInvoicePaidAmount());
			invoice.setInvoiceMonth(dbInvoice.getStartDate()); // tochange as per business
			//invoice.setInvoiceMonth(dbInvoice.getInvoiceMonth());
			try {
				dbInvoiceObject = invoiceRepository.save(invoice);
			} catch (Exception e) {

				// You may want to provide more specific exception handling based on the type of
				// exception thrown
				throw new InvoiceException("Failed to save invoice", e);
			}
		}

		logger.info("invoice dbobj:: " + dbInvoiceObject);
		if (dbInvoiceObject != null && dbInvoiceObject.getId() != null) {
			syncBillsForInvoice(dbInvoiceObject, dbInvoice.getProjectId());
		} else {

			// You may throw an exception, log an error, or handle it based on application's
			// logic
		}

		return dbInvoiceObject;
	}

	// Keeps bills in lockstep with their invoice: if none exist yet for this
	// invoice, create them (first-time invoice); if bills already exist,
	// update their hours/total to match rather than creating duplicates —
	// this is what makes editing an invoice's hours (e.g. from the
	// Generate Invoice grid) propagate to the bills billed against it.
	private void syncBillsForInvoice(Invoice invoice, Long projectId) throws BillsException {
		List<Bills> existingBills = billsRepository.findByInvoiceId(invoice.getId());
		if (existingBills.isEmpty()) {
			createBillsForInvoice(invoice, projectId);
		} else {
			applyInvoiceHoursToBills(invoice, existingBills);
		}
	}

	// Used both by the sync-on-save path above and by the direct
	// PUT /invoices/{id} update endpoint.
	public void applyInvoiceHoursToBills(Invoice invoice, List<Bills> bills) {
		for (Bills bill : bills) {
			bill.setHours(invoice.getHours());
			bill.setTotal(bill.getHours() * bill.getBilling());
			billsRepository.save(bill);
		}
	}

	public void syncBillsHoursForInvoice(Invoice invoice) {
		List<Bills> bills = billsRepository.findByInvoiceId(invoice.getId());
		applyInvoiceHoursToBills(invoice, bills);
	}

	// When an invoice is marked Paid (the client has paid it), every bill
	// billed against it moves from Created to Invoice Cleared. A bill only
	// reaches Paid separately, when the bill itself is paid out.
	public void markBillsInvoiceCleared(Invoice invoice) {
		List<Bills> bills = billsRepository.findByInvoiceId(invoice.getId());
		for (Bills bill : bills) {
			bill.setStatus("Invoice Cleared");
			billsRepository.save(bill);
		}
	}

	// Rerunnable validation pass over every invoice: derives what each of
	// its bills' status *should* be (Invoice Cleared if the invoice is
	// Paid, Created otherwise) and corrects any bill that doesn't already
	// match — this is what backfills bills created before the
	// Created/Invoice Cleared/Paid lifecycle existed (they were stamped
	// with the assignment's Active/Inactive status instead). A bill
	// already at Paid is never touched — that's a further, independent
	// state reached only when the bill itself is paid out, and downgrading
	// it back to Invoice Cleared just because the invoice is Paid would
	// lose that.
	@Transactional
	public Map<String, Integer> validateBillStatusForAllInvoices() {
		List<Invoice> allInvoices = invoiceRepository.getAllInvoices();

		int invoicesChecked = 0;
		int billsUpdated = 0;
		for (Invoice invoice : allInvoices) {
			invoicesChecked++;
			String targetStatus = "paid".equalsIgnoreCase(invoice.getStatus()) ? "Invoice Cleared" : "Created";

			List<Bills> bills = billsRepository.findByInvoiceId(invoice.getId());
			for (Bills bill : bills) {
				if ("paid".equalsIgnoreCase(bill.getStatus())) continue;
				if (targetStatus.equalsIgnoreCase(bill.getStatus())) continue;

				bill.setStatus(targetStatus);
				billsRepository.save(bill);
				billsUpdated++;
			}
		}

		Map<String, Integer> result = new HashMap<>();
		result.put("invoicesChecked", invoicesChecked);
		result.put("billsUpdated", billsUpdated);
		return result;
	}

	// Rerunnable repair pass: a bill's startDate/endDate must always match
	// its invoice's — bills created before a fix to mapAssignmentsToBills
	// (which stamped endDate with the invoice's startDate instead of its
	// endDate) can be out of sync, and this corrects every one of them in
	// one pass, keyed off invoiceId.
	@Transactional
	public Map<String, Integer> validateBillDatesForAllInvoices() {
		List<Invoice> allInvoices = invoiceRepository.getAllInvoices();

		int invoicesChecked = 0;
		int billsUpdated = 0;
		for (Invoice invoice : allInvoices) {
			invoicesChecked++;

			List<Bills> bills = billsRepository.findByInvoiceId(invoice.getId());
			for (Bills bill : bills) {
				boolean startMismatch = !java.util.Objects.equals(bill.getStartDate(), invoice.getStartDate());
				boolean endMismatch = !java.util.Objects.equals(bill.getEndDate(), invoice.getEndDate());
				if (!startMismatch && !endMismatch) continue;

				bill.setStartDate(invoice.getStartDate());
				bill.setEndDate(invoice.getEndDate());
				billsRepository.save(bill);
				billsUpdated++;
			}
		}

		Map<String, Integer> result = new HashMap<>();
		result.put("invoicesChecked", invoicesChecked);
		result.put("billsUpdated", billsUpdated);
		return result;
	}

	// Finds every bill whose invoiceId doesn't match a real row in the
	// invoice table (orphaned by a deleted/never-created invoice), and
	// deletes them unless dryRun is true. Rerunnable/idempotent — a second
	// call with dryRun=false after the first just finds zero.
	@Transactional
	public Map<String, Object> deleteOrphanedBills(boolean dryRun) {
		Set<Long> validInvoiceIds = invoiceRepository.getAllInvoices().stream()
				.map(Invoice::getId)
				.collect(Collectors.toSet());

		List<Bills> allBills = billsRepository.findAll();
		List<Bills> orphaned = allBills.stream()
				.filter(bill -> bill.getInvoiceId() == null || !validInvoiceIds.contains(bill.getInvoiceId()))
				.collect(Collectors.toList());

		if (!dryRun) {
			billsRepository.deleteAll(orphaned);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("billsChecked", allBills.size());
		result.put("orphanedFound", orphaned.size());
		result.put("deleted", !dryRun);
		result.put("orphanedBillIds", orphaned.stream().map(Bills::getBillId).collect(Collectors.toList()));
		return result;
	}

	// One bill per assignment active on the invoice's project during the
	// invoice's month — shared by invoice creation (above) and the
	// existing-invoice backfill (below).
	private void createBillsForInvoice(Invoice invoice, Long projectId) throws BillsException {
		try {
			YearMonth invoiceMonth = YearMonth.from(invoice.getInvoiceMonth());

			List<Assignment> assignmentsList = assignmentRepository.findByProjectId(projectId).stream()
					.filter(assignment -> assignment.getStartDate() != null && assignment.getEndDate() != null)
					.filter(assignment -> {
						YearMonth startMonth = YearMonth.from(assignment.getStartDate());
						YearMonth endMonth = YearMonth.from(assignment.getEndDate());

						return !startMonth.isAfter(invoiceMonth) && !endMonth.isBefore(invoiceMonth);
					})
					.toList();

			for (Assignment assignment : assignmentsList) {
				Bills bill = mapAssignmentsToBills(assignment, invoice);
				billsRepository.save(bill);
			}
		} catch (Exception e) {
			throw new BillsException("Failed to save bills", e);
		}
	}

	// Backfill: create bills for every existing invoice that doesn't have
	// any yet (idempotent — safe to re-run, already-billed invoices are
	// skipped). Needed because bill creation was added after a number of
	// invoices already existed in the database.
	@Transactional
	public int backfillBillsForExistingInvoices() throws BillsException {
		Set<Long> invoiceIdsWithBills = new HashSet<>(billsRepository.findDistinctInvoiceIdsWithBills());
		List<Invoice> allInvoices = invoiceRepository.getAllInvoices();

		int invoicesBilled = 0;
		for (Invoice invoice : allInvoices) {
			if (invoiceIdsWithBills.contains(invoice.getId())) continue;
			if (invoice.getProjectId() == null || invoice.getInvoiceMonth() == null) continue;

			createBillsForInvoice(invoice, invoice.getProjectId());
			invoicesBilled++;
		}
		return invoicesBilled;
	}
	
	  private Bills mapAssignmentsToBills(Assignment assignment, Invoice invoice) {

	        // Create a Bills object and map data from Assignments
	        Bills bill = new Bills();
	        bill.setInvoiceId(invoice.getId());
	        bill.setProjectId(invoice.getProjectId());
	        bill.setAssignmentId(assignment.getAssignmentId());
	        bill.setEmployeeId(assignment.getEmployeeId());
	        bill.setBillDate(invoice.getInvoiceDate());
	        bill.setBilling((float) assignment.getWage());
	        bill.setHours(invoice.getHours());
	        bill.setBillPaidAmount(0);
	        bill.setPaymentDate(invoice.getPaymentDate());
	        bill.setStartDate(invoice.getStartDate());
	        bill.setEndDate(invoice.getEndDate());
	        bill.setInvoiceMonth(invoice.getInvoiceMonth());
	        // Lifecycle: Created (on creation, here) -> Invoice Cleared (when
	        // the invoice this bill is billed against gets marked Paid) ->
	        // Paid (when the bill itself is paid out — set via BillsService).
	        bill.setStatus("Created");
	        bill.setTotal(bill.getHours()*bill.getBilling());
		  bill.setBillType(assignment.getAssignmentType());

	        logger.info("billobj to be saved:: "+bill.toString());
	        return bill;
	    }

	  public List<Invoice> getInvoiceByMonthAndProjectId(String selectedDate, Long projectId) {
			 logger.info(selectedDate+ ":: "+projectId);
			 LocalDate date = LocalDate.parse(selectedDate);
		     String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyyMM"));

			 return invoiceRepository.findByInvoiceByMonthAndProjectId(formattedDate, projectId);
			 
		}
	public List<Invoice> getInvoiceByProjectId( Long projectId) {
		logger.info( "Project ID :: "+projectId);

		return invoiceRepository.findByProjectId( projectId);

	}
	  
	  public boolean isValid(Long value) {
	        return value != null && value != 0L && value != Long.MIN_VALUE;
	    }
	  
}
