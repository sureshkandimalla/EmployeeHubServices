package com.employeehub.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.domain.BasicEmployee;
import com.employeehub.model.Invoice;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.service.EmployeeService;
import java.util.Map;
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
import com.employeehub.model.Bills;
import com.employeehub.repository.BillsRepository;
import com.employeehub.service.BillsService;


@RestController
@RequestMapping("/api/v1/bills")
public class BillsController {
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BillsController.class);
	
	@Autowired
    private BillsRepository billsRepository;
	@Autowired
    private BillsService billsService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private InvoiceRepository invoiceRepository;

    // Each bill only stores its invoice's id, not the invoice's own
    // human-readable number — resolve it with one batch lookup rather than
    // a query per bill.
    private void setInvoiceNumbers(List<com.employeehub.domain.Bills> bills) {
        List<Long> invoiceIds = bills.stream()
                .map(com.employeehub.domain.Bills::getInvoiceId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Long> invoiceNumbersById = invoiceRepository.findAllById(invoiceIds).stream()
                .collect(Collectors.toMap(Invoice::getId, Invoice::getInvoiceNumber));
        bills.forEach(bill -> bill.setInvoiceNumber(invoiceNumbersById.get(bill.getInvoiceId())));
    }


    @PostMapping
	public ResponseEntity<Bills> createBill(@RequestBody Bills bill) throws BillsException {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		Bills savedBill = billsService.createBill(bill);
		return new ResponseEntity<>(savedBill, HttpStatus.CREATED);
	}

	@GetMapping("/{id}")
	public ResponseEntity<Bills> getBillById(@PathVariable Long id) {
		return ResponseEntity.ok(billsService.getBillById(id));
	}

	@PutMapping("/{id}")
	public ResponseEntity<Bills> updateBill(@PathVariable Long id, @RequestBody Bills billDetails) throws BillsException {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		Bills updatedBill = billsService.updateBill(id, billDetails);
		return ResponseEntity.ok(updatedBill);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		billsService.deleteBill(id);
		return ResponseEntity.noContent().build();
	}

    // Every bill across all projects — used by the Projects Overview's
    // Invoices vs Bills chart. Bills has its own project_id column
    // (populated from the invoice's project at bill-creation time); no
    // join through Invoice is needed to attribute a bill to its project.
    @GetMapping("/getAllBills")
    public ResponseEntity<List<Bills>> getAllBills() {
        return ResponseEntity.ok(billsRepository.findAll());
    }

    @GetMapping("/getBillsForCustomer")
    public ResponseEntity<List<Bills>> getBillsForCustomer(@RequestParam(required = true) Long customerId) {
        return ResponseEntity.ok(billsRepository.findByCustomer(customerId));
    }

    @GetMapping("/getBillsForMonthAndYear")
	public ResponseEntity<List<Bills>> getBillsForMonthAndYear(@RequestParam(required = true) String selectedDate) {
    	
    	LocalDate localDate = LocalDate.parse(selectedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    	logger.info("selectedDate:::::"+selectedDate);
        // Format the LocalDate to a string in "yyyymm" format
        String formattedDate = localDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        Optional<List<Bills>> billsList= billsService.findBillsByInvoiceMonth(formattedDate);
        logger.info("billsList:: "+billsList.toString());
    	return billsList.map(bills -> ResponseEntity.ok(bills))
                .orElse(null);
    }
    @GetMapping("/getBillsForProject")
    public ResponseEntity<List<com.employeehub.domain.Bills>> getBillsForProject(@RequestParam(required = true) long projectId) {

        Optional<List<Bills>> billsList= billsService.findBillsForProject(projectId);
        List<com.employeehub.domain.Bills> domainBillsList = billsList.orElseThrow().stream()
                .map(bills -> new com.employeehub.domain.Bills(bills))
                .collect(Collectors.toList());
        List<BasicEmployee> employees=employeeService.getEmployees();
        domainBillsList.stream().forEach(bills -> {
                    bills.setEmployeeName(employees.stream()
                            .filter(emp -> emp.getEmployeeId()==bills.getEmployeeId())
                            .findFirst()
                            .map(BasicEmployee::getName)
                            .orElse("Unknown"));
                    logger.info("bills:: "+ bills);
        }   );
        setInvoiceNumbers(domainBillsList);
        logger.info("billsList:: "+ domainBillsList);
        return billsList.map(bills -> ResponseEntity.ok(domainBillsList))
                .orElse(null);
    }
    @GetMapping("/getBillsForEmployee")
    public ResponseEntity<List<com.employeehub.domain.Bills>> getBillsForEmployee(@RequestParam(required = true) long employeeId) {

        Optional<List<Bills>> billsList= billsService.findBillsForEmployee(employeeId);
        List<com.employeehub.domain.Bills> domainBillsList = billsList.orElseThrow().stream()
                .map(bills -> new com.employeehub.domain.Bills(bills))
                .collect(Collectors.toList());
        List<BasicEmployee> employees=employeeService.getEmployees();
        domainBillsList.stream().forEach(bills -> {
            bills.setEmployeeName(employees.stream()
                    .filter(emp -> emp.getEmployeeId()==bills.getEmployeeId())
                    .findFirst()
                    .map(BasicEmployee::getName)
                    .orElse("Unknown"));
            logger.info("bills:: "+ bills);
        }   );
        setInvoiceNumbers(domainBillsList);
        logger.info("billsList:: "+ domainBillsList);
        return billsList.map(bills -> ResponseEntity.ok(domainBillsList))
                .orElse(null);
    }

}
