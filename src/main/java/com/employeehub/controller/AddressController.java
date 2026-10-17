package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Address;
import com.employeehub.model.Employee;
import com.employeehub.model.Project;
import com.employeehub.repository.AddressRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/address")
public class AddressController {

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private ProjectRepository projectRepository;

	// A row with a null `type` predates this field and is treated as HOME.
	private static String normalizeType(String type) {
		return type == null ? "HOME" : type;
	}

	// HOME addresses keep the dated-history behavior: "updating" closes out
	// whichever row is currently active (endDate = the new period's start
	// date, active = false) and inserts a new one. A row with a null
	// `active` predates this feature and is treated as active, so old
	// single-address records still get closed out correctly the first time
	// they're edited. startDate/endDate on the incoming payload are
	// optional — if the caller didn't provide a start date, it defaults to
	// today.
	//
	// A WORKSITE address (projectId present, from Project Onboarding) works
	// differently: a project's work location doesn't have "periods" the way
	// home address does, so it's a single row per project, updated in place
	// (upsert by projectId) rather than appended to a history.
	@PutMapping("/employee/{employeeId}")
	public ResponseEntity<Address> updateEmployeeAddress(@PathVariable long employeeId,
			@RequestBody Address addressDetails) {
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		Employee employee = employeeRepository.findById(employeeId)
				.orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + employeeId));

		if (addressDetails.getProjectId() != null) {
			return ResponseEntity.ok(upsertWorksiteAddress(employee, addressDetails));
		}

		String type = normalizeType(addressDetails.getType());
		LocalDate startDate = addressDetails.getStartDate() != null ? addressDetails.getStartDate()
				: LocalDate.now();

		List<Address> existing = addressRepository.findByEmployee_EmployeeIdOrderByStartDateDesc(employeeId);
		existing.stream()
				.filter(a -> normalizeType(a.getType()).equals(type) && (a.getActive() == null || a.getActive()))
				.forEach(a -> {
					a.setActive(false);
					if (a.getEndDate() == null) {
						a.setEndDate(startDate);
					}
					addressRepository.save(a);
				});

		Address newAddress = new Address();
		newAddress.setEmployee(employee);
		newAddress.setType(type);
		newAddress.setAddress(addressDetails.getAddress());
		newAddress.setCity(addressDetails.getCity());
		newAddress.setState(addressDetails.getState());
		newAddress.setZipCode(addressDetails.getZipCode());
		newAddress.setCountry(addressDetails.getCountry());
		newAddress.setStartDate(startDate);
		newAddress.setEndDate(addressDetails.getEndDate());
		newAddress.setActive(true);

		return ResponseEntity.ok(addressRepository.save(newAddress));
	}

	private Address upsertWorksiteAddress(Employee employee, Address addressDetails) {
		Project project = projectRepository.findById(addressDetails.getProjectId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Project not exist with id :" + addressDetails.getProjectId()));

		Address worksite = addressRepository.findByProject_ProjectId(project.getProjectId()).orElseGet(Address::new);
		worksite.setEmployee(employee);
		worksite.setProject(project);
		worksite.setType("WORKSITE");
		worksite.setWorkArrangement(addressDetails.getWorkArrangement());
		worksite.setAddress(addressDetails.getAddress());
		worksite.setCity(addressDetails.getCity());
		worksite.setState(addressDetails.getState());
		worksite.setZipCode(addressDetails.getZipCode());
		worksite.setCountry(addressDetails.getCountry());
		if (worksite.getStartDate() == null) {
			worksite.setStartDate(addressDetails.getStartDate() != null ? addressDetails.getStartDate()
					: LocalDate.now());
		}
		worksite.setEndDate(addressDetails.getEndDate());
		worksite.setActive(true);
		return addressRepository.save(worksite);
	}

	// The current work site address for a project, if one's been captured —
	// used to prefill the Work Location fields when editing an existing
	// project. 204 (no body) rather than 404, since "no worksite address
	// yet" is an expected, unexceptional state for older projects.
	@GetMapping("/project/{projectId}")
	public ResponseEntity<Address> getWorksiteAddressForProject(@PathVariable long projectId) {
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		return addressRepository.findByProject_ProjectId(projectId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.noContent().build());
	}

	// Every address period on file for this employee, most recent first —
	// the active HOME one (or the most recently started, for pre-history
	// rows) is shown on the Personnel File page. Pass `type` to scope to
	// just HOME or just WORKSITE rows; omit it to get everything.
	@GetMapping("/employee/{employeeId}/history")
	public ResponseEntity<List<Address>> getAddressHistory(@PathVariable long employeeId,
			@RequestParam(required = false) String type) {
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		List<Address> all = addressRepository.findByEmployee_EmployeeIdOrderByStartDateDesc(employeeId);
		if (type == null) {
			return ResponseEntity.ok(all);
		}
		return ResponseEntity.ok(all.stream().filter(a -> normalizeType(a.getType()).equals(type)).toList());
	}
}
