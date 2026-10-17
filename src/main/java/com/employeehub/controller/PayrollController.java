package com.employeehub.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.model.Payroll;
import com.employeehub.repository.PayrollRepository;
import com.employeehub.service.BillsService;


@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PayrollController.class);
	
	@Autowired
    private PayrollRepository payrollRepository;
	@Autowired
    private BillsService billsService;
	
	
    @GetMapping("/getPayrolls")
	public List<Payroll> getPayrolls() {
    	CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
    	return payrollRepository.findAll();

    }
	@GetMapping("/getPayrollsForEmp")
	public List<Payroll> getPayrolls(@RequestParam(required = true) long employeeId) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		List<Payroll> filteredPayrolls = payrollRepository.findPayrollsWithPayPeriodDatesByEmployeeId(employeeId);

		return filteredPayrolls;

	}

}
