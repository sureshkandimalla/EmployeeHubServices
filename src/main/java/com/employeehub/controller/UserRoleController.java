package com.employeehub.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.UserRole;
import com.employeehub.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Manages the email->role mappings that back frontend page gating (see
// BeanEMS's src/Utils/roleAccess.js and src/Admin/UserRoles.jsx). Write
// operations (grant/change/revoke a role) require the caller to already be
// ADMIN — without that, any authenticated user could PUT their own email
// with role=ADMIN and self-escalate.
@RestController
@RequestMapping("/api/v1/userRoles")
public class UserRoleController {

  @Autowired
  private UserRoleRepository userRoleRepository;

  @GetMapping
  public List<UserRole> getAllUserRoles() {
    return userRoleRepository.findAll();
  }

  // Upsert by email: assigns a role to a new user or changes an existing one.
  @PutMapping
  public ResponseEntity<UserRole> upsertUserRole(@RequestBody UserRole userRole) {
    CurrentUserContext.requireRole("ADMIN");
    UserRole existing = userRoleRepository.findByEmailIgnoreCase(userRole.getEmail()).orElse(null);
    if (existing != null) {
      existing.setRole(userRole.getRole());
      return ResponseEntity.ok(userRoleRepository.save(existing));
    }
    return ResponseEntity.ok(userRoleRepository.save(userRole));
  }

  @DeleteMapping("/{email}")
  public ResponseEntity<Map<String, Boolean>> deleteUserRole(@PathVariable String email) {
    CurrentUserContext.requireRole("ADMIN");
    UserRole userRole = userRoleRepository
      .findByEmailIgnoreCase(email)
      .orElseThrow(() -> new ResourceNotFoundException("no role assigned for email: " + email));

    userRoleRepository.delete(userRole);
    Map<String, Boolean> response = new HashMap<>();
    response.put("deleted", Boolean.TRUE);
    return ResponseEntity.ok(response);
  }
}
