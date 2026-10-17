package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.domain.Dashboard;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Assignment;
import com.employeehub.model.Project;
import com.employeehub.model.Wage;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.BillsRepository;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.repository.ProjectRepository;
import com.employeehub.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/")
public class ProjectController {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProjectController.class);

	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private AssignmentRepository assignmentRepository;
	@Autowired
	private InvoiceRepository invoiceRepository;
	@Autowired
	private BillsRepository billsRepository;


	@GetMapping("/activeProjects")
	public List<com.employeehub.domain.Project> getAllActiveProjects(@RequestParam(required = true) String endDate,
			@RequestParam(required = true) String selectedDate) {

		logger.info("endDate: " + endDate);
		String selectedMonth=selectedDate.substring(0,7);

		var activeProjects = projectRepository.findAllActiveProjectsForMonth(selectedMonth);
		logger.info(activeProjects.toString());
		List<com.employeehub.domain.Project> flattenProjects = new ArrayList<>();
		for (Project project : activeProjects)
			project.getBillRates().forEach(billrate -> {
				flattenProjects.add(projectService.createProject(project, billrate, selectedDate));
			});


		  flattenProjects.forEach(project -> { System.out.println(project); });

		
		return flattenProjects;
	}

	@GetMapping({"/activeProjectsForInvoiceByEmployee", "/projects/activeProjectsForInvoiceByEmployee", "/projects/activeProjectsForInvoice", "/activeProjectsForInvoice"})
	public List<com.employeehub.domain.Project> activeProjectsForInvoiceByEmployee(@RequestParam(required = true) String employeeId) {

		var activeProjects = projectRepository.findAllProjectsByEmployee(Long.parseLong(employeeId));
		logger.info(activeProjects.toString());
		List<com.employeehub.domain.Project> flattenProjects = new ArrayList<>();
		for (Project project : activeProjects)
			project.getBillRates().forEach(billrate -> {
				flattenProjects.addAll(projectService.createProjectForInvoice(project, billrate));
			});

		/*
		 * flattenProjects.forEach(project -> { System.out.println(project); });
		 */
		flattenProjects.forEach(project -> {

		});

		return flattenProjects;
	}
	@GetMapping("/allActiveProjects")
	public List<com.employeehub.domain.Project> getAllActiveProjects() {

		//logger.info("endDate: " + endDate);
		String endDate=LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		String selectedDate=LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		var activeProjects = projectRepository.findAllActiveProjectsByDate(endDate);
		logger.info(activeProjects.toString());
		List<com.employeehub.domain.Project> flattenProjects = new ArrayList<>();
		activeProjects.stream().forEach(project -> {
			project.getBillRates().forEach(billrate -> {
				flattenProjects.add(projectService.createProject(project, billrate, selectedDate));
			});
		});

		/*
		 * flattenProjects.forEach(project -> { System.out.println(project); });
		 */

		return flattenProjects;
	}


	@GetMapping("/projectDashboard")
	public com.employeehub.domain.Dashboard projectDashboard() {


		var activeProjects = projectRepository.findAllActiveProjectsByDate(LocalDate.now().toString());
		logger.info(null, activeProjects.size());
		com.employeehub.domain.Dashboard dashboardData = new Dashboard();
		
		double totalWageBillRate = activeProjects.stream()
			    .flatMap(project -> project.getBillRates().stream())
			    .mapToDouble(billRate -> billRate.getWage())
			    .sum();

		// below form assignments table based on active projectId
		
		//var activeAssignments = assignmentRepository.findActiveAssignmentsByEndDate(LocalDate.now().toString());
		//double totalAssignWage = activeAssignments.stream().mapToDouble(billRate -> billRate.getWage()).sum();
		
		List<Long> projectIds = activeProjects.stream().map(project -> project.getProjectId())
				.collect(Collectors.toList());

		// Fetch sum of wages from assignments table
		System.out.println(projectIds);
		var totalWageFromAssignments=0;
		//var totalWageFromAssignments = assignmentRepository.getTotalWageByProjectIds(projectIds);
		
		Optional.ofNullable(activeProjects.size())
        .ifPresentOrElse(
                size -> dashboardData.setActiveProjects(size),
                () -> dashboardData.setActiveProjects(0)
        );
		Optional.ofNullable(totalWageBillRate).ifPresentOrElse(wage -> dashboardData.setTotalRevenue(wage),() -> dashboardData.setTotalRevenue(0));
		Optional.ofNullable(totalWageFromAssignments).ifPresentOrElse(wage -> dashboardData.setTotalCost(wage),() -> dashboardData.setTotalCost(0));
		logger.info("dashboardData:: "+dashboardData.toString());
		
		return dashboardData;
	}

 Map<String,Long> getWages(Project project,Wage billrate){
	var assignments=project.getAssignments();
	Map<String,Long> assignmentMap=new HashMap<>();
	assignments.forEach(assignment ->{
		if((assignment.getStartDate().isEqual(billrate.getStartDate()) || assignment.getStartDate().isAfter(billrate.getStartDate()))
		&& (assignment.getEndDate().isEqual(billrate.getEndDate())  || assignment.getEndDate().isEqual(billrate.getEndDate()))){
			assignmentMap.put(assignment.getAssignmentType(), (long)assignment.getWage());
		}
	} );
	return assignmentMap;
}
	@GetMapping("/getProjects")
	public List<com.employeehub.domain.Project> getProjects() {
		List<com.employeehub.domain.Project> flattenProjects = flattenProjects(projectRepository.findAll());
		if(!flattenProjects.isEmpty()) {
			logger.info("flattenProjects.size():: "+flattenProjects.size());
		}
		return flattenProjects;
	}

	// Scoped version of getProjects() for a single customer — used by
	// CustomerFullDetailsComponent.jsx so it doesn't have to fetch every
	// project in the tenant just to filter down to one customer's own.
	@GetMapping("/getProjectsByCustomer/{customerId}")
	public List<com.employeehub.domain.Project> getProjectsByCustomer(@PathVariable Long customerId) {
		return flattenProjects(projectRepository.findAllProjectsByCustomer(customerId));
	}

	// Same per-bill-rate flattening getProjects()/getProjectsByCustomer() both
	// need — a project with no bill rate records falls back to its own
	// dates with a $0 bill rate so it still shows up, rather than being
	// silently dropped.
	private List<com.employeehub.domain.Project> flattenProjects(List<Project> repoProjects) {
		List<com.employeehub.domain.Project> flattenProjects = new ArrayList<>();
		repoProjects.forEach(project -> {
			if (project.getBillRates() == null || project.getBillRates().isEmpty()) {
				Wage fallbackWage = new Wage();
				fallbackWage.setWage(0f);
				fallbackWage.setStartDate(project.getStartDate());
				fallbackWage.setEndDate(project.getEndDate());
				flattenProjects.add(projectService.createProject(project, fallbackWage, null));
			} else {
				project.getBillRates().forEach(billrate -> {
					flattenProjects.add(projectService.createProject(project, billrate, null));
				});
			}
		});
		return flattenProjects;
	}

	public com.employeehub.domain.Project updateBillRate(com.employeehub.domain.Project project, Assignment assignment) {

		return project;
	}

	@PostMapping("" +
			"/saveOnBoardProject")
	public ResponseEntity<Map<String, Object>> createProject(@RequestBody com.employeehub.domain.Project project) {

		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		ResponseEntity<Map<String, Object>> savedProject = projectService.saveProject(project);
		return savedProject;
	}

	@GetMapping("/projects/{id}")
	public ResponseEntity<Project> getProjectById(@PathVariable Long id) {
		Project project = projectRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Project not exist with id :" + id));
		return ResponseEntity.ok(project);
	}
	// Same raw-entity (nested employee/customer/billRates) shape either way —
	// ProjectGrid.jsx reads whichever of the two params it was given.
	@GetMapping("/projects")
	public ResponseEntity<List<Project>> getProjectByEmpId(
			@RequestParam(required = false) Long employeeId,
			@RequestParam(required = false) Long customerId) {
		List<Project> project = customerId != null
				? projectRepository.findAllProjectsByCustomer(customerId)
				: projectRepository.findAllProjectsByEmployee(employeeId);
		return ResponseEntity.ok(project);
	}

	@PutMapping("/projects/{id}")
	public ResponseEntity<Project> updateProject(@PathVariable Long id, @RequestBody Project projectDetails) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		Project project = projectRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Project not exist with id :" + id));

		// Note: startDate/endDate shown in the /getProjects list view are derived
		// from the project's Wage record, not simple pass-through fields on
		// Project itself — see WageController#updateWage for those. Status is a
		// real field here; getProjects() prefers it over its date-based
		// auto-compute once it's been explicitly set (see ProjectService).
		project.setProjectName(projectDetails.getProjectName());
		project.setInvoiceTerm(projectDetails.getInvoiceTerm());
		project.setPaymentTerm(projectDetails.getPaymentTerm());
		project.setStatus(projectDetails.getStatus());
		project.setClient(projectDetails.getClient());
		// weekStartDay is newer than the four fields above and not every
		// existing caller of this endpoint knows to send it — null-checked
		// so an old request body doesn't silently clear a configured value.
		if (projectDetails.getWeekStartDay() != null && !projectDetails.getWeekStartDay().isBlank()) {
			project.setWeekStartDay(projectDetails.getWeekStartDay());
		}

		Project updatedProject = projectRepository.save(project);
		return ResponseEntity.ok(updatedProject);
	}

	// Lets the frontend show a "this will also delete N invoices/bills/
	// assignments" warning before the user commits to deleteProject below.
	@GetMapping("/projects/{id}/deletionImpact")
	public ResponseEntity<Map<String, Integer>> getProjectDeletionImpact(@PathVariable Long id) {
		Map<String, Integer> impact = new HashMap<>();
		impact.put("invoices", invoiceRepository.findByProjectId(id).size());
		impact.put("bills", billsRepository.findByProjectId(id).size());
		impact.put("assignments", assignmentRepository.findByProjectId(id).size());
		return ResponseEntity.ok(impact);
	}

	// Invoices and bills aren't JPA relations on Project (unlike
	// assignments/billRates, which cascade via CascadeType.ALL below), so
	// they're deleted explicitly here — otherwise they'd be left behind
	// still pointing at the now-deleted project_id.
	@DeleteMapping("/projects/{id}")
	public ResponseEntity<Map<String, Integer>> deleteProject(@PathVariable Long id) {
		CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
		Project project = projectRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Project not exist with id :" + id));

		var bills = billsRepository.findByProjectId(id);
		billsRepository.deleteAll(bills);

		var invoices = invoiceRepository.findByProjectId(id);
		invoiceRepository.deleteAll(invoices);

		int assignmentsDeleted = project.getAssignments() != null ? project.getAssignments().size() : 0;
		projectRepository.delete(project);

		Map<String, Integer> response = new HashMap<>();
		response.put("invoicesDeleted", invoices.size());
		response.put("billsDeleted", bills.size());
		response.put("assignmentsDeleted", assignmentsDeleted);
		return ResponseEntity.ok(response);
	}



}
