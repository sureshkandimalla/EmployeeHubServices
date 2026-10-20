package com.employeehub.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.employeehub.model.UserRole;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    Optional<UserRole> findByEmailIgnoreCase(String email);

    // Recipient list for role-targeted notifications (e.g. "email HR about
    // this new hire") — see NotificationService.
    List<UserRole> findByRoleIgnoreCase(String role);
}
