package com.employeehub.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.repository.VendorRepository;
import com.employeehub.service.VendorService;
import com.employeehub.model.Vendor;
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
@RequestMapping("/api/v1/vendors")
public class VendorController {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VendorController.class);

  @Autowired
  private VendorRepository vendorRepository;

  @Autowired
  private VendorService vendorService;

  @GetMapping("/getAllVendors")
  public List<Vendor> getAllVendors() {
    return vendorRepository.findAll();
  }

  @PostMapping("/saveOnBoardDetails")
  public ResponseEntity<Optional<Vendor>> createVendor(@RequestBody com.employeehub.domain.Vendor vendor) {

	  CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
	  logger.info("vendor:: "+vendor.toString());
    Optional<Vendor> respVendor = vendorService.saveVendor(vendor);

    return ResponseEntity.ok(respVendor);
  }

  @GetMapping("/vendors/{id}")
  public ResponseEntity<Vendor> getVendorById(@PathVariable Long id) {
    Vendor vendor = vendorRepository
      .findById(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("vendor not exist with id :" + id)
      );
    return ResponseEntity.ok(vendor);
  }

  // Partial update: same pattern as CustomerController#updateCustomer — each
  // field is only applied when actually present in the request body, so an
  // old/partial request body doesn't null out the rest.
  @PutMapping("/vendors/{id}")
  public ResponseEntity<Vendor> updateVendor(
    @PathVariable Long id,
    @RequestBody Vendor vendorDetails
  ) {
    CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
    Vendor vendor = vendorRepository
      .findById(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("vendor not exist with id :" + id)
      );

    if (vendorDetails.getVendorName() != null) vendor.setVendorName(vendorDetails.getVendorName());
    if (vendorDetails.getVendorCompanyName() != null) vendor.setVendorCompanyName(vendorDetails.getVendorCompanyName());
    if (vendorDetails.getVendorEmail() != null) vendor.setVendorEmail(vendorDetails.getVendorEmail());
    if (vendorDetails.getVendorPhone() != null) vendor.setVendorPhone(vendorDetails.getVendorPhone());
    if (vendorDetails.getVendorStatus() != null) vendor.setVendorStatus(vendorDetails.getVendorStatus());
    if (vendorDetails.getEin() != null) vendor.setEin(vendorDetails.getEin());
    if (vendorDetails.getWebsite() != null) vendor.setWebsite(vendorDetails.getWebsite());
    if (vendorDetails.getVendorAddress() != null) vendor.setVendorAddress(vendorDetails.getVendorAddress());
    if (vendorDetails.getVendorStartDate() != null) vendor.setVendorStartDate(vendorDetails.getVendorStartDate());
    if (vendorDetails.getVendorEndDate() != null) vendor.setVendorEndDate(vendorDetails.getVendorEndDate());
    if (vendorDetails.getVendorType() != null) vendor.setVendorType(vendorDetails.getVendorType());
    if (vendorDetails.getPaymentTerms() != null) vendor.setPaymentTerms(vendorDetails.getPaymentTerms());
    if (vendorDetails.getPaymentPolicy() != null) vendor.setPaymentPolicy(vendorDetails.getPaymentPolicy());

    Vendor updatedVendor = vendorRepository.save(vendor);
    return ResponseEntity.ok(updatedVendor);
  }

  @DeleteMapping("/vendors/{id}")
  public ResponseEntity<Map<String, Boolean>> deleteVendor(
    @PathVariable Long id
  ) {
    CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
    Vendor vendor = vendorRepository
      .findById(id)
      .orElseThrow(() ->
        new ResourceNotFoundException("vendor not exist with id :" + id)
      );

    vendorRepository.delete(vendor);
    Map<String, Boolean> response = new HashMap<>();
    response.put("deleted", Boolean.TRUE);
    return ResponseEntity.ok(response);
  }
}
