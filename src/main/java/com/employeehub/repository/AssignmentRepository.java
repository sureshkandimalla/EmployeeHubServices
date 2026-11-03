package com.employeehub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.employeehub.model.Assignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query(
            value = "SELECT * FROM assignment a where DATE_FORMAT(a.start_date,'%Y%m')<=? and DATE_FORMAT(a.end_date,'%Y%m')>=?",
            nativeQuery = true)
    List<Assignment> findAllActiveAssignment(String startDate,String endDate);

    @Query(
            value = "SELECT * FROM assignment a where a.project_id =? ",
            nativeQuery = true)
	List<Assignment> findByProjectId(Long projectId);
    
    @Query(
            value = "SELECT * FROM assignment a where DATE_FORMAT(a.end_date,'%Y-%m-%d')>=?",
            nativeQuery = true)
    Optional<List<Assignment>> findActiveAssignmentsByEndDate(String endDate);
    
    @Query(value = "SELECT SUM(wage) FROM assignment WHERE project_id IN :projectIds", nativeQuery = true)
    double getTotalWageByProjectIds(@Param("projectIds") List<Long> projectIds);

    @Query(value =" select e.first_name as firstName,e.last_name as lastName,assignment_id as assignmentId,wage as wage, "+
            "assignment_type as assignmentType, a.status as status, a.start_date as startDate,a.end_date as endDate,"+
            "a.last_updated as lastUpdatedDate from assignment a,employees e "+
            "where a.employee_id=e.employee_id and a.project_id=?",
            nativeQuery = true)
    List<Object[]> findAssignmentsForProject(Long projectId);

    @Query(value = "select p.project_id as projectId, p.project_name as projectName, a.assignment_id as assignmentId, "+
            "a.wage as wage, a.assignment_type as assignmentType, a.status as status, "+
            "a.start_date as startDate, a.end_date as endDate from assignment a, project p "+
            "where a.project_id = p.project_id and a.employee_id = ?",
            nativeQuery = true)
    List<Object[]> findAssignmentsForEmployee(Long employeeId);

    // Backs the bulk Timesheet entry page: every active assignment across
    // all employees/projects, joined with the names needed to display it
    // (employee, project, customer) in one row. "Active" is computed from
    // end_date rather than trusting the stored status column, which is
    // only recomputed when the assignment itself is next saved/edited — an
    // assignment whose end_date has since passed would otherwise keep
    // showing up as Active indefinitely.
    @Query(value = "select e.first_name as firstName, e.last_name as lastName, "+
            "p.project_id as projectId, p.project_name as projectName, "+
            "c.customer_company_name as customerName, "+
            "a.assignment_id as assignmentId, a.employee_id as employeeId, a.wage as wage, "+
            "a.status as status, a.start_date as startDate, a.end_date as endDate "+
            "from assignment a "+
            "join employees e on a.employee_id = e.employee_id "+
            "join project p on a.project_id = p.project_id "+
            "left join customer c on p.vendor_id = c.customer_id "+
            "where (a.end_date is null or a.end_date >= CURDATE()) "+
            "and (e.company_name is null or e.company_name <> 'Referral')",
            nativeQuery = true)
    List<Object[]> findAllActiveAssignmentsWithDetails();

    // Backs the Payroll Eligibility page: every assignment whose active
    // window overlaps a given pay period at all (not just "currently
    // active" — a pay period can be in the past), joined with the same
    // employee/project/customer names as findAllActiveAssignmentsWithDetails.
    @Query(value = "select e.first_name as firstName, e.last_name as lastName, "+
            "p.project_id as projectId, p.project_name as projectName, "+
            "c.customer_company_name as customerName, "+
            "a.assignment_id as assignmentId, a.employee_id as employeeId, a.wage as wage, "+
            "a.status as status, a.start_date as startDate, a.end_date as endDate, "+
            "COALESCE(NULLIF(e.employee_type, ''), NULLIF(e.tax_term, '')) as employeeType, "+
            "e.tax_term as taxTerm, e.company_name as companyName "+
            "from assignment a "+
            "join employees e on a.employee_id = e.employee_id "+
            "join project p on a.project_id = p.project_id "+
            "left join customer c on p.vendor_id = c.customer_id "+
            "where a.start_date <= ?2 and (a.end_date is null or a.end_date >= ?1) "+
            "and (e.company_name is null or e.company_name <> 'Referral')",
            nativeQuery = true)
    List<Object[]> findAssignmentsEligibleForPeriod(LocalDate periodStart, LocalDate periodEnd);

    // Every assignment that has ever existed (no active/date filtering at
    // all) joined with the employee/project names needed to display it —
    // backs the Missing Timesheets report, which needs to walk each
    // assignment's *entire* start-to-end window (including long-finished
    // ones) to find months with no submitted timesheet, not just its
    // currently-active state.
    @Query(value = "select e.first_name as firstName, e.last_name as lastName, "+
            "p.project_id as projectId, p.project_name as projectName, "+
            "a.assignment_id as assignmentId, a.employee_id as employeeId, "+
            "a.start_date as startDate, a.end_date as endDate "+
            "from assignment a "+
            "join employees e on a.employee_id = e.employee_id "+
            "join project p on a.project_id = p.project_id "+
            "where (e.company_name is null or e.company_name <> 'Referral') "+
            "and a.start_date is not null",
            nativeQuery = true)
    List<Object[]> findAllAssignmentsWithDetails();

}
