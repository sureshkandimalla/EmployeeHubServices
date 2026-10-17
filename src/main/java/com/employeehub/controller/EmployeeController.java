package com.employeehub.controller;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.domain.BasicEmployee;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.service.EmployeeService;
import com.employeehub.domain.DashboardEmployeeDetails;
import com.employeehub.exception.EmployeeServiceException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Employee;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EmployeeController.class);

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private EmployeeService employeeService;

	/*
	 * get all employees
	 */

	@GetMapping("/getAllEmployees")
	public List<Employee> getAllEmployees() {
		// Full Employee (SSN, DOB, annualPay) — previously open to ANY
		// authenticated user, even one with no role row at all. /workforce is
		// intentionally reachable by HR, ACCOUNTING, and IMMIGRATION alike
		// (see roleAccess.js ROUTE_ROLES), so this only closes the "no role
		// at all" gap rather than narrowing who the frontend already lets in.
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		return employeeRepository.findAllSorted().stream()
				.sorted(Comparator.comparingLong(Employee::getEmployeeId))
				.collect(Collectors.toList());
	}

	@GetMapping("/getEmployees")
	public List<BasicEmployee> getEmployees() {
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		return employeeRepository.findAllSorted().stream()
				.sorted(Comparator.comparingLong(Employee::getEmployeeId))
				.map(emp -> new BasicEmployee(BigInteger.valueOf(emp.getEmployeeId()), emp.getFirstName() + " " + emp.getLastName(), emp.getStatus()))
				.collect(Collectors.toList());
	}

	@GetMapping("/employees")
	public List<Employee> getAllEmployeesNew() {
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		return employeeRepository.findAllSorted().stream()
				.sorted(Comparator.comparingLong(Employee::getEmployeeId))
				.collect(Collectors.toList());
	}
	// create employee rest api
	@PostMapping("/saveOnBoardDetails")
	public ResponseEntity<Optional<Employee>> createEmployee(@RequestBody com.employeehub.domain.Employee employee)
			throws EmployeeServiceException {

		CurrentUserContext.requireRole("ADMIN", "HR");
		logger.info("employee:: " + employee.toString());
		Optional<Employee> respEmployee = employeeService.saveEmployeeDetails(employee);

		logger.info("respEmployee:: " + respEmployee.toString());

		return ResponseEntity.ok(respEmployee);

	}


	@PostMapping("/saveEmployees")
	public ResponseEntity<Optional<Employee>> saveEmployees(@RequestBody com.employeehub.domain.Employee[] employee)
			throws EmployeeServiceException {

		CurrentUserContext.requireRole("ADMIN", "HR");
		System.out.println(employee);
		Arrays.stream(employee).sequential().forEach(emp -> {
			try {
				employeeService.saveEmployee(emp);
			} catch (EmployeeServiceException e) {
				throw new RuntimeException(e);
			}
		});
	//	employeeService.saveEmployeesDetails(employee);
		ResponseEntity<Optional<Employee>> ok = ResponseEntity.ok(null);
		return ok;

	}

	/*
	 * get all employees count by status returns key-value of all emp with status
	 */

	@GetMapping("/employeesCountByStatus")
	public List<Map<String, String>> getEmployeesCountByStatus() {

		List<Map<String, String>> empStatus = employeeRepository.getEmpCountByStatus();
		logger.info("empStatus:: " + empStatus.toString());
		return empStatus;
	}

	/*
	 * get all employees List by status returns employees List with name, role etc
	 */

	@GetMapping("/employeesListByStatus")
	public ResponseEntity getEmployeesListByStatus(@RequestParam(required = true) String status) {
		logger.info("status:: "+status);
		
		if (status == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Status cannot be null");
		}

		Optional<List<DashboardEmployeeDetails>> empByStatusList = employeeService.getEmpListByStatus(status);
		
		logger.info("empByStatusList:: " + empByStatusList.toString());
		return ResponseEntity.of(empByStatusList);
	}

	// get employees by immigration/visa type
	@GetMapping("/employeesByImmigration")
	public ResponseEntity<List<Employee>> getEmployeesByImmigration(@RequestParam(required = true) String visaType) {
		logger.info("visaType:: " + visaType);
		List<Employee> employees = employeeRepository.getEmployeesByVisa(visaType);
		return ResponseEntity.ok(employees);
	}

	// get employee by id rest api
	@GetMapping("/employee/{id}")
	public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		Employee employee = employeeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + id));
		return ResponseEntity.ok(employee);
	}

	// update employee rest api

	@PutMapping("/{id}")
	public ResponseEntity<Employee> updateEmployee(@PathVariable Long id, @RequestBody Employee employeeDetails) {
		// Inline-edit on the /workforce grid is reachable by HR, ACCOUNTING,
		// and IMMIGRATION alike (see roleAccess.js), so this stays open to
		// any assigned role rather than narrowing to HR only.
		CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION");
		Employee employee = employeeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + id));

		employee.setFirstName(employeeDetails.getFirstName());
		employee.setLastName(employeeDetails.getLastName());
		employee.setEmailId(employeeDetails.getEmailId());
		employee.setPhone(employeeDetails.getPhone());
		employee.setDob(employeeDetails.getDob());
		employee.setSsn(employeeDetails.getSsn());
		employee.setVisa(employeeDetails.getVisa());
		employee.setTaxTerm(employeeDetails.getTaxTerm());
		employee.setReferredBy(employeeDetails.getReferredBy());
		employee.setGender(employeeDetails.getGender());
		employee.setStartDate(employeeDetails.getStartDate());
		employee.setEndDate(employeeDetails.getEndDate());
		employee.setDesignation(employeeDetails.getDesignation());
		employee.setEmploymentType(employeeDetails.getEmploymentType());
		employee.setEmployeeType(employeeDetails.getEmployeeType());
		employee.setVendorId(employeeDetails.getVendorId());
		employee.setStatus(employeeDetails.getStatus());
		employee.setLocation(employeeDetails.getLocation());
		employee.setPrimarySkills(employeeDetails.getPrimarySkills());
		employee.setSecondarySkills(employeeDetails.getSecondarySkills());
		employee.setWorkCity(employeeDetails.getWorkCity());
		employee.setWorkCountry(employeeDetails.getWorkCountry());
		employee.setResourceType(employeeDetails.getResourceType());
		employee.setEmployeeDept(employeeDetails.getEmployeeDept());
		employee.setAnnualPay(employeeDetails.getAnnualPay());
		employee.setPayrollStart(employeeDetails.getPayrollStart());
		employee.setEverifyStatus(employeeDetails.getEverifyStatus());
		employee.setI9(employeeDetails.getI9());
		employee.setPAF(employeeDetails.getPAF());
		employee.setInsurance(employeeDetails.getInsurance());
		employee.setCompanyName(employeeDetails.getCompanyName());

		Employee updatedEmployee = employeeRepository.save(employee);
		return ResponseEntity.ok(updatedEmployee);
	}

	// delete employee rest api
	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Boolean>> deleteEmployee(@PathVariable Long id) {
		CurrentUserContext.requireRole("ADMIN", "HR");
		Employee employee = employeeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + id));

		employeeRepository.delete(employee);
		Map<String, Boolean> response = new HashMap<>();
		response.put("deleted", Boolean.TRUE);
		return ResponseEntity.ok(response);
	}
}
