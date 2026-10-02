package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
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

  @Column(columnDefinition = "TEXT")
  private String description;
}
