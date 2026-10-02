package com.employeehub.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.employeehub.model.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

	List<Address> findByEmployee_EmployeeIdOrderByStartDateDesc(long employeeId);

	// A project's WORKSITE address is a single current row, not a dated
	// history like HOME — unique per project.
	Optional<Address> findByProject_ProjectId(long projectId);
}
