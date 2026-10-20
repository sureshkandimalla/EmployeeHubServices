package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Generic file attachment, reused across entity types (Insurance, Customer
// MSAs, Project POs, Employee docs, ...) rather than one table per type —
// entityType + entityId is the only thing that varies. The actual bytes
// live in S3 (one bucket per tenant); this row is just the pointer + the
// metadata a grid needs to list/download/delete it.
@Setter
@Getter
@Entity
@Table(name = "Document")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String entityType;
  private Long entityId;
  private String fileName;

  @Column(columnDefinition = "TEXT")
  private String s3Key;

  private String contentType;
  private Long sizeBytes;
  private String uploadedBy;
  private LocalDateTime uploadedDate;

  // What kind of document this is (Visa, H1B, Passport, I94, Education,
  // Other, ...) — a free string rather than a Java enum so the list of
  // categories can grow without a migration; the frontend owns the
  // dropdown's option list.
  private String documentType;

  // Free-form refinement within a documentType (e.g. "Initial" vs
  // "Extension" for an H1B approval) — multiple documents can share the
  // same (documentType, subType) pair, which is what makes a grid row
  // expandable (see EmployeeDocumentsPanel.jsx on the frontend).
  private String subType;

  private LocalDate validFrom;
  private LocalDate validUntil;

  // The document's own identifier as printed on it (passport number, I-94
  // number, receipt number, ...) — distinct from `id`, which is this row's
  // own database key.
  private String documentNumber;

  @Column(columnDefinition = "TEXT")
  private String description;

  // Set the first time the expiration-check job emails about this document
  // (see NotificationService#checkExpiringDocuments) — null means "not
  // alerted yet". Stops the same expiring document from re-emailing every
  // day it stays inside the lead-time window; re-uploading a renewed
  // document creates a fresh row (new id, this field unset), so the old
  // one's alert history doesn't carry over.
  private LocalDate expirationAlertSentAt;
}
