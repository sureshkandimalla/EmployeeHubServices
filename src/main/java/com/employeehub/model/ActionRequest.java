package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

// A non-admin's request to Archive or Delete a record, for an admin to
// review and act on — see rowActions.js/PendingRequests.jsx. Same generic
// type+entityId shape as Note/Document (entityId matches that type's own
// primary key). Enforcement is frontend-only (matches every other role
// check in this app — the backend has no concept of roles at all today);
// this table only records the request and its outcome, it never performs
// the actual archive/delete itself — approving a request just replays the
// same archive/delete call the requesting grid would have made directly.
@Setter
@Getter
@Entity
@Table(name = "action_request")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ActionRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long requestId;

  private String type;
  private Long entityId;

  // Snapshot of a human-readable label for the entity (e.g. project name,
  // employee name) at request time, purely for display on the Pending
  // Requests page — avoids that page needing to know how to look up and
  // label every entity type.
  private String entityLabel;

  // "ARCHIVE" or "DELETE"
  private String action;

  // "PENDING" | "APPROVED" | "REJECTED"
  private String status;

  private String requestedBy;

  @CreationTimestamp
  private LocalDateTime requestedDate;

  private String resolvedBy;
  private LocalDateTime resolvedDate;
}
