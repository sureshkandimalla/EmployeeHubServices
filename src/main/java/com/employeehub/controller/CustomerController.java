package com.employeehub.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.repository.CustomerRepository;
import com.employeehub.service.CustomerService;
import com.employeehub.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CustomerController.class);
	
  @Autowired
  private CustomerRepository customerRepository;
  
  @Autowired
  private CustomerService customerService;

  @GetMapping("/getAllCustomers")
  public List<Customer> getAllCustomers() {
    return customerRepository.findAll();
  }

  @PostMapping("/saveOnBoardDetails")
  public ResponseEntity<Optional<Customer>> createCustomer(@RequestBody com.employeehub.domain.Customer customer) {
	  
	  CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
	  logger.info("customer:: "+customer.toString());
    Optional<Customer> respCustomer = customerService.saveCustomer(customer);
    
    return ResponseEntity.ok(respCustomer);
  }

  @GetMapping("/customers/{id}")
  public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
    Customer customer = customerRepository
      .findById(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("customer not exist with id :" + id)
      );
    return ResponseEntity.ok(customer);
  }

  // Partial update: the Customer Details grid (CustomerDetails.jsx) round-trips
  // every field it shows as editable, but not every caller of this endpoint
  // does — each field is only applied when actually present in the request
  // body, so an old/partial request body doesn't null out the rest (same
  // pattern as ProjectController#updateProject / AssignmentController#updateAssignment).
  @PutMapping("/customers/{id}")
  public ResponseEntity<Customer> updateCustomer(
    @PathVariable Long id,
    @RequestBody Customer customerDetails
  ) {
    CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
    Customer customer = customerRepository
      .findById(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("customer not exist with id :" + id)
      );

    if (customerDetails.getCustomerName() != null) customer.setCustomerName(customerDetails.getCustomerName());
    if (customerDetails.getCustomerCompanyName() != null) customer.setCustomerCompanyName(customerDetails.getCustomerCompanyName());
    if (customerDetails.getCustomerEmail() != null) customer.setCustomerEmail(customerDetails.getCustomerEmail());
    if (customerDetails.getCustomerPhone() != null) customer.setCustomerPhone(customerDetails.getCustomerPhone());
    if (customerDetails.getCustomerStatus() != null) customer.setCustomerStatus(customerDetails.getCustomerStatus());
    if (customerDetails.getEin() != null) customer.setEin(customerDetails.getEin());
    if (customerDetails.getWebsite() != null) customer.setWebsite(customerDetails.getWebsite());
    if (customerDetails.getCustomerAddress() != null) customer.setCustomerAddress(customerDetails.getCustomerAddress());
    if (customerDetails.getCustomerStartDate() != null) customer.setCustomerStartDate(customerDetails.getCustomerStartDate());
    if (customerDetails.getCustomerEndDate() != null) customer.setCustomerEndDate(customerDetails.getCustomerEndDate());
    if (customerDetails.getCreditLimit() != null) customer.setCreditLimit(customerDetails.getCreditLimit());
    if (customerDetails.getParentCompany() != null) customer.setParentCompany(customerDetails.getParentCompany());
    if (customerDetails.getBillingContact() != null) customer.setBillingContact(customerDetails.getBillingContact());
    if (customerDetails.getApContact() != null) customer.setApContact(customerDetails.getApContact());
    if (customerDetails.getStandardCurrency() != null) customer.setStandardCurrency(customerDetails.getStandardCurrency());
    if (customerDetails.getMsaStatus() != null) customer.setMsaStatus(customerDetails.getMsaStatus());
    if (customerDetails.getDefaultBillingMethod() != null) customer.setDefaultBillingMethod(customerDetails.getDefaultBillingMethod());
    if (customerDetails.getPaymentTerms() != null) customer.setPaymentTerms(customerDetails.getPaymentTerms());
    if (customerDetails.getNotes() != null) customer.setNotes(customerDetails.getNotes());

    Customer updatedCustomer = customerRepository.save(customer);
    return ResponseEntity.ok(updatedCustomer);
  }

  @DeleteMapping("/customers/{id}")
  public ResponseEntity<Map<String, Boolean>> deletecustomer(
    @PathVariable Long id
  ) {
    CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
    Customer customer = customerRepository
      .findById(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("customer not exist with id :" + id)
      );

    customerRepository.delete(customer);
    Map<String, Boolean> response = new HashMap<>();
    response.put("deleted", Boolean.TRUE);
    return ResponseEntity.ok(response);
  }
}
