package com.employeehub.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.employeehub.domain.WageType;
import com.employeehub.model.Assignment;
import com.employeehub.model.Employee;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.employeehub.model.Customer;
import com.employeehub.domain.ProjectStatus;
import com.employeehub.model.Invoice;
import com.employeehub.model.Project;
import com.employeehub.model.Wage;
import com.employeehub.repository.CustomerRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.ProjectRepository;

@Service
public class ProjectService {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProjectService.class);
	
    @Autowired
    private InvoiceService invoiceService;
    
	@Autowired
    private EmployeeRepository employeeRepository;
	@Autowired
    private ProjectRepository projectRepository;
	@Autowired
    private CustomerRepository customerRepository;
    
	  public com.employeehub.domain.Project  createProject(Project project, Wage wage, String selectedDate){
		    com.employeehub.domain.Project projectDomain=new com.employeehub.domain.Project();
		    projectDomain.setProjectId(project.getProjectId());
		    projectDomain.setWageId(wage.getWageId());
		    projectDomain.setProjectName(project.getProjectName());
		    projectDomain.setClientName(project.getClient());
		    projectDomain.setInvoiceTerm(project.getInvoiceTerm());
		    projectDomain.setPaymentTerm(project.getPaymentTerm());
		    projectDomain.setStartDate(wage.getStartDate());
		    projectDomain.setEndDate(wage.getEndDate());
		    projectDomain.setBillRate(wage.getWage());
		    projectDomain.setEmployeeId(project.getEmployee().getEmployeeId());
		    projectDomain.setEmployeeName(project.getEmployee().getFirstName()+" "+project.getEmployee().getLastName());
		    projectDomain.setEmployeeEmail(project.getEmployee().getEmailId());
		    projectDomain.setCompanyName(project.getEmployee().getCompanyName());
		    projectDomain.setCustomerId(project.getCustomer().getCustomerId());
		    projectDomain.setCustomerName(project.getCustomer().getCustomerCompanyName());
		    projectDomain.setStartDate(wage.getStartDate());
		    projectDomain.setEndDate(wage.getEndDate());
		  LocalDate today = LocalDate.now();
		  // to get multiple assignments if employee pay if we need to create 2 diffrent projects if employee pay is diff
		  /*List<Assignment> empPayAssignments = project.getAssignments().stream()
				  .filter(assignment -> WageType.EMP_PAY.equals(assignment.getAssignmentType()))
				  .collect(Collectors.toList());*/
		  //TODO check for employee wages if there are multiple
		  Optional<Assignment> empAssignment = project.getAssignments().stream()
				  .filter(assignment -> (WageType.EMP_PAY.toString().equals(assignment.getAssignmentType()) && assignment.getStatus().equalsIgnoreCase("Active")))
				  .findFirst()
				  .or(() -> project.getAssignments().stream()
						  .filter(assignment -> WageType.EMP_PAY.toString().equals(assignment.getAssignmentType()))
						  .max(Comparator.comparing(Assignment::getStartDate))
				  );

		  //projectDomain.setEmployerTax((float) (projectDomain.getEmployeePay()*0.8));
		  /*Optional<Double> empPayWage = project.getAssignments().stream()
				  .filter(assignment -> (WageType.EMP_PAY.toString().equals(assignment.getAssignmentType())))
				  *//*.filter(assignment -> {
					  return (assignment.getStartDate().isAfter(project.getStartDate()) || assignment.getStartDate().isEqual(project.getStartDate())) &&
							  (assignment.getEndDate().isBefore(today) || assignment.getEndDate().isEqual(today));
				  })*//*
				  .map(Assignment::getWage)
				  .findFirst();*/
		  if(empAssignment.isPresent()) {
			  projectDomain.setEmployeePay(((float) empAssignment.get().getWage()));
			  Optional<Employee> employee = employeeRepository.findById(empAssignment.get().getEmployeeId());
			  // Employer tax only applies to W2 employees — the assignment's own
			  // tax-type flag isn't always kept in sync with the employee's real
			  // type, so also check the employee record directly and zero out
			  // taxes for C2C regardless of what the assignment says.
			  boolean isC2CEmployee = employee.isPresent() && "C2C".equalsIgnoreCase(employee.get().getEmployeeType());
			 if( empAssignment.get().getAssignmentTaxType().equals("W2") && !isC2CEmployee) {
				 if(employee.isPresent() && employee.get().getVisa() != null && employee.get().getVisa().contains("OPT"))
					 projectDomain.setEmployerTax(0);
				 else
					 projectDomain.setEmployerTax((float) ((projectDomain.getEmployeePay() == 0) ? 0L : projectDomain.getEmployeePay() * .08));
				 projectDomain.setExpenseInternal(projectDomain.getEmployerTax());
			 }
		  }

		  //project.getAssignments().stream().forEach(assignment ->{if(WageType.EMP_PAY.equals(assignment.getAssignmentType())?assignment.getWage()});
		  // A manually-set status (via the Projects grid) always wins; only
		  // fall back to auto-computing Active/Inactive from the dates when
		  // the project has never had an explicit status set.
		  if (project.getStatus() != null && !project.getStatus().isBlank()) {
			  projectDomain.setStatus(project.getStatus());
		  } else if ((wage.getEndDate() != null && wage.getEndDate().isBefore(today)) ||
				  (project.getEndDate() != null && project.getEndDate().isBefore(today))) {
			  projectDomain.setStatus(ProjectStatus.INACTIVE.toString());
		  } else {
			  projectDomain.setStatus(ProjectStatus.ACTIVE.toString());
		  }
		  projectDomain.setExpenseExternal((float) project.getAssignments().stream()
				  .filter(assignment -> !WageType.EMP_PAY.toString().equals(assignment.getAssignmentType()))  // Exclude EMP_PAY
				  .mapToDouble(Assignment::getWage)  // Sum wages
				  .sum());
		  projectDomain.setNet(projectDomain.getBillRate()- projectDomain.getEmployeePay()- projectDomain.getExpenseExternal()- projectDomain.getExpenseInternal());
		//  projectDomain.setExpenseExternal((float) project.getAssignments().stream().mapToDouble(Assignment::getWage).sum());
		//  getExpense(wage,project.getAssignments());
		    
		    //to add cond'n if any requred for below other than just selectedDate
			if (selectedDate != null) {
				List<Invoice> monthlyInvoices = invoiceService.getInvoiceByMonthAndProjectId(selectedDate,
						project.getProjectId());

				
				monthlyInvoices.stream().filter(invoice -> invoice.getProjectId().equals(project.getProjectId()))
						.findAny().ifPresentOrElse(invoice -> {
							logger.info("id: "+invoice.getId()+"hours: "+invoice.getHours());
							projectDomain.setHours(invoice.getHours());
							projectDomain.setInvoiceId(invoice.getId());
							projectDomain.setInvoiceNumber(invoice.getInvoiceNumber());
							projectDomain.setTotal(invoice.getTotal());

						}, () -> {System.out.println("No matching invoice found");});
				 
				
				logger.info("monthlyInvoices::: "+monthlyInvoices.size());


			}
		    return projectDomain;

		  }


	public List<com.employeehub.domain.Project>  createProjectForInvoice(Project project, Wage wage){
		com.employeehub.domain.Project projectDomain=new com.employeehub.domain.Project();
		projectDomain.setProjectId(project.getProjectId());
		projectDomain.setProjectName(project.getProjectName());
		projectDomain.setClientName(project.getClient());
		projectDomain.setInvoiceTerm(project.getInvoiceTerm());
		projectDomain.setPaymentTerm(project.getPaymentTerm());
		projectDomain.setWeekStartDay(resolveWeekStartDay(project).name());
		projectDomain.setStartDate(wage.getStartDate());
		projectDomain.setEndDate(wage.getEndDate());
		projectDomain.setBillRate(wage.getWage());
		projectDomain.setEmployeeId(project.getEmployee().getEmployeeId());
		projectDomain.setEmployeeName(project.getEmployee().getFirstName()+" "+project.getEmployee().getLastName());
		projectDomain.setCustomerId(project.getCustomer().getCustomerId());
		projectDomain.setCustomerName(project.getCustomer().getCustomerCompanyName());
		projectDomain.setStartDate(wage.getStartDate());
		projectDomain.setEndDate(wage.getEndDate());
		LocalDate today = LocalDate.now();


		//Here
		LocalDate startDate = wage.getStartDate();
		LocalDate endDate = wage.getEndDate();
		if (endDate == null || endDate.isAfter(today)) {
			endDate = today;
		}
		System.out.println("startDate: "+startDate+" endDate: "+endDate);
		List<com.employeehub.domain.Project> dominList = new ArrayList<>();

		List<Invoice> projectInvoices = invoiceService.getInvoiceByProjectId(
				project.getProjectId());

		String invoiceTerm = project.getInvoiceTerm();
		DayOfWeek weekStartDay = resolveWeekStartDay(project);

		if ("1".equals(invoiceTerm)) {
			// Weekly: one row per calendar week, anchored to the project's
			// configured week-start day (defaults to Monday).
			addWeekAlignedRows(dominList, projectDomain, project, wage, today, projectInvoices,
					startDate, endDate, weekStartDay, 1, 6);
		} else if ("2".equals(invoiceTerm)) {
			// Biweekly: one row per two-week span, same anchor day.
			addWeekAlignedRows(dominList, projectDomain, project, wage, today, projectInvoices,
					startDate, endDate, weekStartDay, 2, 13);
		} else if ("6".equals(invoiceTerm)) {
			// Once in 4 Weeks: one row per four-week span, same anchor day.
			addWeekAlignedRows(dominList, projectDomain, project, wage, today, projectInvoices,
					startDate, endDate, weekStartDay, 4, 27);
		} else if ("5".equals(invoiceTerm)) {
			// Semi-Monthly: two rows per month — 1st-15th and 16th-end.
			LocalDate monthCursor = startDate.withDayOfMonth(1);
			while (!monthCursor.isAfter(endDate)) {
				LocalDate firstHalfStart = monthCursor;
				LocalDate firstHalfEnd = monthCursor.withDayOfMonth(15);
				dominList.add(buildInvoiceRow(projectDomain, project, wage, today, firstHalfStart, firstHalfEnd,
						projectInvoices.stream().filter(invoice -> firstHalfStart.equals(invoice.getStartDate())).findAny()));

				LocalDate secondHalfStart = monthCursor.withDayOfMonth(16);
				LocalDate secondHalfEnd = monthCursor.withDayOfMonth(monthCursor.lengthOfMonth());
				dominList.add(buildInvoiceRow(projectDomain, project, wage, today, secondHalfStart, secondHalfEnd,
						projectInvoices.stream().filter(invoice -> secondHalfStart.equals(invoice.getStartDate())).findAny()));

				monthCursor = monthCursor.plusMonths(1);
			}
		} else {
			// Monthly (also the fallback for "Special"/unrecognized terms).
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
			LocalDate current = startDate;
			while (!current.isAfter(endDate)) {
				LocalDate periodStart = current.withDayOfMonth(1); // first day of the current month
				LocalDate periodEnd = current.withDayOfMonth(current.lengthOfMonth()); // last day of the current month
				String selectedMonthYear = current.format(formatter);

				Optional<Invoice> matched = projectInvoices.stream()
						.filter(invoice -> invoice.getInvoiceMonth().format(formatter).equals(selectedMonthYear))
						.findAny();

				dominList.add(buildInvoiceRow(projectDomain, project, wage, today, periodStart, periodEnd, matched));
				current = current.plusMonths(1);
			}
		}

		return dominList;

	}

	// Shared by Weekly/Biweekly/Once-in-4-Weeks: all three are spans of a
	// fixed number of weeks anchored to the project's configured
	// week-start day, matched against any existing invoice by its exact
	// period start date.
	private void addWeekAlignedRows(List<com.employeehub.domain.Project> dominList, com.employeehub.domain.Project projectDomain,
			Project project, Wage wage, LocalDate today, List<Invoice> projectInvoices,
			LocalDate startDate, LocalDate endDate, DayOfWeek weekStartDay, int stepWeeks, int spanDays) {
		LocalDate cursor = startDate.with(TemporalAdjusters.previousOrSame(weekStartDay));
		boolean isFirstRow = true;
		while (!cursor.isAfter(endDate)) {
			// The grid-aligned cursor can fall before the project's actual
			// start date (e.g. project starts on a Sunday, grid anchors to
			// Monday) — only the very first period clips forward to the
			// real start date, so the first invoice doesn't bill days
			// before the engagement began. The period's end date, and every
			// later period, stays on the regular weekStartDay grid.
			final LocalDate periodStart = (isFirstRow && cursor.isBefore(startDate)) ? startDate : cursor;
			LocalDate periodEnd = cursor.plusDays(spanDays);
			isFirstRow = false;

			Optional<Invoice> matched = projectInvoices.stream()
					.filter(invoice -> periodStart.equals(invoice.getStartDate()))
					.findAny();

			dominList.add(buildInvoiceRow(projectDomain, project, wage, today, periodStart, periodEnd, matched));
			cursor = cursor.plusWeeks(stepWeeks);
		}
	}

	// Every week-based term (Weekly/Biweekly/Once-in-4-Weeks) anchors to
	// this project's own configured start-of-week day; defaults to Monday
	// when not set (covers both a genuinely unconfigured project and any
	// unparseable/legacy value, rather than throwing).
	private DayOfWeek resolveWeekStartDay(Project project) {
		String configured = project.getWeekStartDay();
		if (configured == null || configured.isBlank()) {
			return DayOfWeek.MONDAY;
		}
		try {
			return DayOfWeek.valueOf(configured.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return DayOfWeek.MONDAY;
		}
	}

	// One row for a single invoice period (a month, or a week-based term's
	// span) — shared by both the monthly and week-aligned branches above.
	private com.employeehub.domain.Project buildInvoiceRow(com.employeehub.domain.Project template, Project project, Wage wage,
			LocalDate today, LocalDate periodStart, LocalDate periodEnd, Optional<Invoice> matchedInvoice) {
		com.employeehub.domain.Project row = template.clone();
		row.setStartDate(periodStart);
		row.setEndDate(periodEnd);

		if ((wage.getEndDate() != null && wage.getEndDate().isBefore(today)) ||
				(project.getEndDate() != null && project.getEndDate().isBefore(today)))
			row.setStatus(ProjectStatus.INACTIVE.toString());
		else
			row.setStatus(ProjectStatus.ACTIVE.toString());

		matchedInvoice.ifPresentOrElse(invoice -> {
			logger.info("id: " + invoice.getId() + "hours: " + invoice.getHours());
			row.setHours(invoice.getHours());
			row.setInvoiceId(invoice.getId());
			row.setInvoiceNumber(invoice.getInvoiceNumber());
			row.setTotal(invoice.getTotal());
		}, () -> logger.info("No matching invoice found for period {} - {}", periodStart, periodEnd));

		return row;
	}

	public ResponseEntity<Map<String, Object>> saveProject(com.employeehub.domain.Project project) {

			logger.info("employeeid : "+project.getEmployeeId());
		
			Project dbProject = new Project();
			dbProject.setClient(project.getClient());
			dbProject.setEmployeeId(project.getEmployeeId());
			dbProject.setCustomerId(project.getCustomerId());
			dbProject.setEndDate(project.getEndDate());
			dbProject.setStartDate(project.getStartDate());
			dbProject.setStatus(project.getStatus());
			dbProject.setLastUpdated(LocalDate.now());
			dbProject.setProjectName(project.getProjectName());
			dbProject.setInvoiceTerm(project.getInvoiceTerm());
			dbProject.setPaymentTerm(project.getPaymentTerm());
			dbProject.setWeekStartDay(project.getWeekStartDay());

		Optional<Employee> optionalEmployee = employeeRepository.findById(project.getEmployeeId());
		Optional<Customer> optionalCustomer = customerRepository.findById(project.getCustomerId());
		Project savedProject;

		Wage wage =new Wage();
		// The work order's own dates, not necessarily the project's —
		// falls back to the project's dates when omitted (older callers,
		// e.g. the Excel import in MasterDataLoadService).
		wage.setEndDate(project.getWorkOrderEndDate() != null ? project.getWorkOrderEndDate() : project.getEndDate());
		wage.setLastUpdated(LocalDate.now());
		wage.setStartDate(project.getWorkOrderStartDate() != null ? project.getWorkOrderStartDate() : project.getStartDate());
		wage.setWageType("Billing");
		wage.setWage(project.getBillRate());
		wage.setCreatedDate(LocalDate.now());
		// Using Optional to handle null list gracefully
		Optional.ofNullable(dbProject.getBillRates())
				.orElseGet(() -> {
					List<Wage> newList = new ArrayList<>();
					dbProject.setBillRates(newList);
					return newList;
				})
				.add(wage);
		if (optionalEmployee.isPresent() && optionalCustomer.isPresent()) {
		    Employee employee = optionalEmployee.get();
		    Customer customer = optionalCustomer.get();

		    dbProject.setEmployee(employee);
		    dbProject.setCustomer(customer);

		    logger.info("employeeid & customerid in project table: " + project.getEmployeeId() + " " + project.getCustomerId());
		    savedProject = projectRepository.save(dbProject);
		} else {
		    // Handle the case where either the employee or the customer is not found
			Map<String, Object> notFound = new HashMap<>();
			notFound.put("message", "Employee or Customer not found");
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound);
		}
		// projectId/wageId are returned so the frontend can immediately
		// attach a document (Purchase Order) to the project's just-created
		// initial work order — see DocumentController, entityType
		// "WorkOrderPO", entityId = wageId (not projectId — a project can
		// have several work orders, each with its own PO).
		Map<String, Object> response = new HashMap<>();
		response.put("message", "Project data saved Succesfully");
		response.put("projectId", savedProject.getProjectId());
		response.put("wageId", wage.getWageId());
		return ResponseEntity.status(HttpStatus.OK).body(response);

	}
}
