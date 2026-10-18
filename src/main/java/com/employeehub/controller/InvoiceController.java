package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.domain.DashboardEmployeeDetails;
import com.employeehub.model.Assignment;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.service.InvoiceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.employeehub.exception.BillsException;
import com.employeehub.exception.InvoiceException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Invoice;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

//import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.ReassociateMatch.x;

@RestController
@RequestMapping("/api/v1/invoice")
public class InvoiceController {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InvoiceController.class);

	@Autowired
	private InvoiceRepository invoiceRepository;
	@Autowired
	private AssignmentRepository assignmentRepository;
	@Autowired
	private InvoiceService invoiceService;

	@PostMapping("/addInvoices")
	public ResponseEntity<String> createInvoice(@RequestBody List<com.employeehub.domain.Invoice> invoices)
			throws InvoiceException, BillsException {

		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		List<com.employeehub.domain.Invoice> filteredInvoices = invoices.stream()
				.filter(invoice -> isValid(invoice.getInvoiceNumber())) // only invoiceNumber as hours and total can be 0/null
				.collect(Collectors.toList());

		// filteredInvoices.forEach(System.out::println);
		logger.info("size:: " + filteredInvoices.size());
		for (com.employeehub.domain.Invoice invoice : filteredInvoices) {
			String formatSelectedDate = invoice.getFormatSelectedDate();
			logger.info("formatSelectedDate: " + formatSelectedDate);
			invoiceService.createInvoiceObject(invoice, formatSelectedDate);
			// invoiceRepository.save(dbInvoiceObject);
		}

		return new ResponseEntity<>("Invoices created successfully", HttpStatus.CREATED);
	}

	public boolean isValid(Long value) {
		return value != null && value != 0L && value != Long.MIN_VALUE;
	}

	// One-off/rerunnable backfill: creates bills for any existing invoice
	// that doesn't have any yet. Safe to call repeatedly — already-billed
	// invoices are skipped.
	@PostMapping("/backfillBills")
	public ResponseEntity<Map<String, Object>> backfillBills() throws BillsException {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		int invoicesBilled = invoiceService.backfillBillsForExistingInvoices();
		Map<String, Object> result = new HashMap<>();
		result.put("invoicesBilled", invoicesBilled);
		return ResponseEntity.ok(result);
	}

	// Rerunnable: walks every invoice and corrects any of its bills whose
	// status doesn't match what the invoice's current status implies
	// (Invoice Cleared for Paid invoices, Created otherwise). Safe to call
	// repeatedly — already-correct bills, and any bill already at Paid,
	// are left alone.
	@PostMapping("/validateBillStatus")
	public ResponseEntity<Map<String, Integer>> validateBillStatus() {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		return ResponseEntity.ok(invoiceService.validateBillStatusForAllInvoices());
	}

	// Rerunnable: walks every invoice and corrects any of its bills whose
	// startDate/endDate don't match the invoice's. Safe to call repeatedly.
	@PostMapping("/validateBillDates")
	public ResponseEntity<Map<String, Integer>> validateBillDates() {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		return ResponseEntity.ok(invoiceService.validateBillDatesForAllInvoices());
	}

	// Rerunnable cleanup: deletes every bill whose invoiceId doesn't match a
	// real invoice. Defaults to a dry run (dryRun=true) so a caller can see
	// what would be deleted before actually deleting — pass dryRun=false to
	// commit.
	@PostMapping("/deleteOrphanedBills")
	public ResponseEntity<Map<String, Object>> deleteOrphanedBills(
			@RequestParam(defaultValue = "true") boolean dryRun) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		return ResponseEntity.ok(invoiceService.deleteOrphanedBills(dryRun));
	}


	@GetMapping("/invoices/{id}")
	public ResponseEntity<Invoice> getInvoiceById(@PathVariable Long id) {
		Invoice invoice = invoiceRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Invoice not exist with id: " + id));
		return ResponseEntity.ok(invoice);
	}
	

	@GetMapping("/invoicesForMonth")
	public ResponseEntity<List<Invoice>> getInvoiceForMonth(String month) {
		/*
		 * Invoice invoice = invoiceRepository.findById(id) .orElseThrow(() -> new
		 * ResourceNotFoundException("Invoice not exist with id: " + id));
		 */
		month = "202303";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
		YearMonth yearMonth = YearMonth.parse(month, formatter);

		LocalDate startMonth = yearMonth.atDay(1);
		LocalDate endMonth = yearMonth.atEndOfMonth();
		List<Assignment> assignmentList = assignmentRepository.findAllActiveAssignment(month, month);
		List x = assignmentList.stream().map(assignment -> {
			Invoice newInvoice = new Invoice(startMonth, endMonth);
			// newInvoice.setAssignment(assignment);
			invoiceRepository.save(newInvoice);
			return newInvoice;
		}).collect(Collectors.toList());
		return ResponseEntity.ok(x);
	}


	@GetMapping("/getAllInvoices")
	public ResponseEntity<List<Invoice>> getAllInvoices() {
		return ResponseEntity.ok(invoiceRepository.getAllInvoices());
	}

	
	@GetMapping("/invoicesForMonthAndYear")
	public ResponseEntity<List<Invoice>> getInvoiceForMonthAndYear(@RequestParam(required = true) String selectedDate, @RequestParam(required = false) String status) {

		logger.info("selectedDate:::::" + selectedDate);
		logger.info("status:::::" + status);
		LocalDate localDate = LocalDate.parse(selectedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		String formattedDate = localDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
		logger.info("formattedDate:::::" + formattedDate);
		List<Invoice> invoiceMonthList;
		
		if(status == null || status.equalsIgnoreCase("viewAll")) {
		 invoiceMonthList = invoiceRepository.findAllInvoicesForTheMonth(formattedDate);
		}else {
			invoiceMonthList = invoiceRepository.findAllInvoicesForTheMonthAndStatus(formattedDate,status);
		}
		
		//to add logic based on status
		
		logger.info("invoiceMonthList:: " + invoiceMonthList.toString());
		return ResponseEntity.ok(invoiceMonthList);
	}
	
	
	/*
	 * get all employees count by status returns key-value of all emp with status
	 */

	@GetMapping("/invoicesCountByStatus")
	public List<Map<String, String>> getInvoicesCountByStatus() {

		List<Map<String, String>> invoiceStatus = invoiceRepository.getInvoiceCountByStatus();
		logger.info("invoiceStatus:: " + invoiceStatus.toString());
		return invoiceStatus;
	}

	

	@PutMapping("/invoices/{id}")
	public ResponseEntity<Invoice> updateInvoice(@PathVariable Long id, @RequestBody Invoice invoiceDetails) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		invoiceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice not exist with id: " + id));
		// The path id is the only authoritative identity — never trust the
		// request body's id (a client could send a different one, which
		// would silently retarget/overwrite an unrelated invoice row).
		invoiceDetails.setId(id);
		switch (invoiceDetails.getStatus().toLowerCase()) {
			case "paid":
				if (invoiceDetails.getInvoicePaidAmount() == 0) {
					// Invoice PaidAmount is Invoice Amount minus Discounts —
					// same rule the frontend applies live as Discounts is edited.
					invoiceDetails.setInvoicePaidAmount(
							Math.max(invoiceDetails.getTotal() - invoiceDetails.getDiscounts(), 0));
				}
				// Backstop behind the frontend's paid-date prompt — an invoice
				// saved as Paid should never end up with no paid date.
				if (invoiceDetails.getInvoicePaidDate() == null) {
					invoiceDetails.setInvoicePaidDate(java.time.LocalDate.now());
				}
				break;
			case "unpaid":
				if (invoiceDetails.getInvoicePaidAmount() != 0) {
					invoiceDetails.setInvoicePaidAmount(0L);
				}
				break;
			default:
				// Handle other statuses if needed
				break;
		}
		Invoice updatedInvoice = invoiceRepository.saveAndFlush(invoiceDetails);

		// Keep the bills billed against this invoice in sync with its hours
		// (and the total that's derived from them).
		invoiceService.syncBillsHoursForInvoice(updatedInvoice);

		// Invoice marked Paid -> its bills move from Created to Invoice Cleared.
		if ("paid".equalsIgnoreCase(updatedInvoice.getStatus())) {
			invoiceService.markBillsInvoiceCleared(updatedInvoice);
		}

		return ResponseEntity.ok(updatedInvoice);
	}

	
	@DeleteMapping("/invoices/{id}")
	public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		Invoice invoice = invoiceRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Invoice not exist with id: " + id));

		invoiceRepository.delete(invoice);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/getInvoices")
	public ResponseEntity<Invoice> getInvoices(@PathVariable Long id) {
		Invoice invoice = invoiceRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Invoice not exist with id: " + id));
		return ResponseEntity.ok(invoice);
	}


	@GetMapping("/activeInvoices/{monthYear}")
	public ResponseEntity<List<Invoice>> getActiveInvoicesForMonth(@PathVariable String monthYear) {
		List<Invoice> invoiceList = invoiceRepository.findAllActiveInvoicesForTheMonth(monthYear, monthYear);
		return ResponseEntity.ok(invoiceList);
	}
	@GetMapping("/getInvoicesForEmployee")
	public ResponseEntity<List<Invoice>> getInvoicesForEmployee(@RequestParam(required = true) Long employeeId) {
		logger.info("employeeId:: "+employeeId);

		List<Invoice> invoiceList = invoiceRepository.findByEmployee(employeeId);
		return ResponseEntity.ok(invoiceList);
	}
	@GetMapping("/getInvoicesForProject")
	public ResponseEntity<List<Invoice>> getInvoicesForProject(@RequestParam(required = true) Long projectId) {
		logger.info("employeeId:: "+projectId);

		List<Invoice> invoiceList = invoiceRepository.findByProjectId(projectId).stream().toList();
		return ResponseEntity.ok(invoiceList);
	}

	@GetMapping("/getInvoicesForCustomer")
	public ResponseEntity<List<Invoice>> getInvoicesForCustomer(@RequestParam(required = true) Long customerId) {
		List<Invoice> invoiceList = invoiceRepository.findByCustomer(customerId);
		return ResponseEntity.ok(invoiceList);
	}



}
