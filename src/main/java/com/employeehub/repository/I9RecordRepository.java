package com.employeehub.repository;

import com.employeehub.model.i9.I9Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface I9RecordRepository extends JpaRepository<I9Record, Long> {

    List<I9Record> findByEmployeeIdOrderByValidFromDesc(Long employeeId);

    List<I9Record> findByEmployeeIdInOrderByValidFromDesc(List<Long> employeeIds);
}
