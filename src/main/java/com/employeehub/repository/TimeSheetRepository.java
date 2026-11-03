package com.employeehub.repository;

import com.employeehub.model.TimeSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSheetRepository extends JpaRepository<TimeSheet, Long> {
    List<TimeSheet> findByAssignmentIdAndWorkDateBetweenOrderByWorkDate(Long assignmentId, LocalDate start, LocalDate end);
    List<TimeSheet> findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(Long employeeId, LocalDate start, LocalDate end);
    List<TimeSheet> findByWorkDateBetweenOrderByWorkDate(LocalDate start, LocalDate end);
    Optional<TimeSheet> findByAssignmentIdAndWorkDate(Long assignmentId, LocalDate workDate);

    // Backs the Timesheets grid (every employee/project/month timesheet, one
    // row per day) — joined with the names needed to display it, grouping
    // into one row per employee/assignment/month happens in
    // TimeSheetService#getTimesheetSummaries since that needs the
    // most-restrictive-status-wins rule, which SQL can't express cleanly.
    @Query(value = "select e.first_name as firstName, e.last_name as lastName, " +
            "p.project_id as projectId, p.project_name as projectName, " +
            "t.assignment_id as assignmentId, t.employee_id as employeeId, " +
            "t.work_date as workDate, t.hours as hours, t.status as status, t.last_updated as lastUpdated, " +
            "e.email_id as employeeEmail " +
            "from timesheet t " +
            "join employees e on t.employee_id = e.employee_id " +
            "join project p on t.project_id = p.project_id",
            nativeQuery = true)
    List<Object[]> findAllWithNames();
}
