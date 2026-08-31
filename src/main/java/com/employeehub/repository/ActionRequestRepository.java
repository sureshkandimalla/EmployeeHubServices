package com.employeehub.repository;

import com.employeehub.model.ActionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionRequestRepository extends JpaRepository<ActionRequest, Long> {

    List<ActionRequest> findByStatusOrderByRequestedDateDesc(String status);

    List<ActionRequest> findAllByOrderByRequestedDateDesc();
}
