package com.employeehub.service;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.config.TenantContext;
import com.employeehub.domain.DownloadLink;
import com.employeehub.domain.PresignedUpload;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Coi;
import com.employeehub.model.Customer;
import com.employeehub.model.Document;
import com.employeehub.model.Employee;
import com.employeehub.model.Project;
import com.employeehub.model.Vendor;
import com.employeehub.model.Wage;
import com.employeehub.repository.CoiRepository;
import com.employeehub.repository.CustomerRepository;
import com.employeehub.repository.DocumentRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.ProjectRepository;
import com.employeehub.repository.VendorRepository;
import com.employeehub.repository.WageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DocumentService {

    private static final DateTimeFormatter KEY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private WageRepository wageRepository;
    @Autowired
    private CoiRepository coiRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private VendorRepository vendorRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private S3Client s3Client;
    @Autowired
    private S3Presigner s3Presigner;

    // One bucket per tenant (see S3Config) — same "bean"/"intellan"/
    // "kkassociates" keys TenantRoutingDataSource already uses for the
    // JDBC side, just applied to a bucket name instead of a DB URL.
    private String bucketForCurrentTenant() {
        String tenant = TenantContext.get();
        return "employeehub-documents-" + tenant;
    }

    // Every document is filed under its owning Employee — Project and
    // WorkOrderPO docs resolve back to one via their own relations — so S3
    // reads like a per-person file cabinet rather than a flat pile of
    // entityType folders. A COI (and a Vendor/Customer doc) has no employee
    // at all, so those file under the Vendor/Customer instead. entityId is
    // NOT always the id of this root folder (a WorkOrderPO's entityId is
    // the Wage's own id, not the employee's), so each type resolves it
    // differently.
    private record RootFolder(String prefix, Long id) {}

    private RootFolder resolveRootFolder(String entityType, Long entityId) {
        switch (entityType) {
            case "Employee":
                return new RootFolder("employees", entityId);
            case "Project": {
                Project project = projectRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Project not exist with id :" + entityId));
                return new RootFolder("employees", project.getEmployee().getEmployeeId());
            }
            case "WorkOrderPO": {
                Wage wage = wageRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Wage not exist with id :" + entityId));
                Project project = projectRepository.findById(wage.getProjectId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Project not exist with id :" + wage.getProjectId()));
                return new RootFolder("employees", project.getEmployee().getEmployeeId());
            }
            case "Vendor":
                return new RootFolder("vendors", entityId);
            case "Customer":
                return new RootFolder("customers", entityId);
            case "COI": {
                Coi coi = coiRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("COI not exist with id :" + entityId));
                if (coi.getVendorId() != null) return new RootFolder("vendors", coi.getVendorId());
                if (coi.getCustomerId() != null) return new RootFolder("customers", coi.getCustomerId());
                throw new ResourceNotFoundException("COI " + entityId + " has neither a vendor nor a customer");
            }
            default:
                throw new IllegalArgumentException("Unknown document entityType: " + entityType);
        }
    }

    // Employee/Project/WorkOrderPO docs are gated to ADMIN/HR, or the
    // employee the document is actually filed under (matched by login
    // email, since that's how everyone authenticates — see AuthController).
    // Vendor/Customer/COI docs have no owning employee, so those are gated
    // to ADMIN/ACCOUNTING instead, matching ENTITY_ROLES.vendor/customer on
    // the frontend. Without this, any authenticated user — any role, any
    // employee — could read or delete any other employee's/vendor's
    // documents by entityId or by the raw Document id alone.
    private void requireAccess(String entityType, Long entityId) {
        RootFolder root = resolveRootFolder(entityType, entityId);
        String role = CurrentUserContext.role();
        if ("ADMIN".equals(role)) return;

        if ("employees".equals(root.prefix())) {
            if ("HR".equals(role)) return;
            Employee owner = employeeRepository.findById(root.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + root.id()));
            String callerEmail = CurrentUserContext.email();
            if (callerEmail != null && callerEmail.equalsIgnoreCase(owner.getEmailId())) return;
        } else {
            if ("ACCOUNTING".equals(role)) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this document");
    }

    public PresignedUpload presignUpload(String entityType, Long entityId, String fileName, String contentType,
                                          String documentType, String subType, String validFrom, String validUntil) {
        requireAccess(entityType, entityId);
        RootFolder root = resolveRootFolder(entityType, entityId);

        // Readable, not random: <ownerId>_<ownerName>_<documentType>_<subType>_<validFrom>_<validUntil>_<timestamp>.<ext>
        // The millisecond timestamp is the uniqueifier (replacing a UUID) —
        // it stays readable/sortable in the S3 console while still making a
        // collision between two uploads of the same type+subType for the
        // same owner (e.g. an H1B approval renewed years apart) practically
        // impossible; without *some* uniqueifier, a second such upload would
        // silently overwrite the first object in S3 even though both
        // Document rows remain in the database. documentNumber (passport #,
        // receipt #, ...) is deliberately left out of the key — S3 keys show
        // up in logs/URLs, and that's not a place to put document numbers.
        StringBuilder name = new StringBuilder(String.valueOf(root.id()));
        appendKeySegment(name, ownerDisplayName(root));
        appendKeySegment(name, documentType);
        appendKeySegment(name, subType);
        appendKeySegment(name, validFrom);
        appendKeySegment(name, validUntil);
        appendKeySegment(name, KEY_TIMESTAMP.format(LocalDateTime.now()));

        String ext = fileExtension(fileName);
        String s3Key = root.prefix() + "/" + name + (ext.isEmpty() ? "" : "." + ext);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketForCurrentTenant())
                .key(s3Key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putRequest)
                .build();
        String url = s3Presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedUpload(url, s3Key);
    }

    private String ownerDisplayName(RootFolder root) {
        switch (root.prefix()) {
            case "employees": {
                Employee emp = employeeRepository.findById(root.id())
                        .orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + root.id()));
                return emp.getFirstName() + " " + emp.getLastName();
            }
            case "vendors": {
                Vendor vendor = vendorRepository.findById(root.id())
                        .orElseThrow(() -> new ResourceNotFoundException("Vendor not exist with id :" + root.id()));
                return vendor.getVendorCompanyName();
            }
            case "customers": {
                Customer customer = customerRepository.findById(root.id())
                        .orElseThrow(() -> new ResourceNotFoundException("Customer not exist with id :" + root.id()));
                return customer.getCustomerCompanyName();
            }
            default:
                return "";
        }
    }

    // Appends "_<cleaned>" — alphanumeric only, so no raw value (free-typed
    // documentType/subType included) can inject a "/" and escape the key's
    // folder prefix. Blank/null segments are skipped entirely rather than
    // leaving a bare "_" in the name.
    private static void appendKeySegment(StringBuilder sb, String raw) {
        if (raw == null) return;
        // Hyphens pass through (so a date reads "2026-01-01", not
        // "2026_01_01") — everything else not alphanumeric/hyphen,
        // including "/", becomes "_".
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9-]+", "_").replaceAll("^[_-]+|[_-]+$", "");
        if (!cleaned.isEmpty()) sb.append("_").append(cleaned);
    }

    private static String fileExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).replaceAll("[^a-zA-Z0-9]", "");
    }

    public Document confirm(Document meta) {
        requireAccess(meta.getEntityType(), meta.getEntityId());
        // Never trust a client-supplied id: save() merges/updates when a
        // non-null id matches an existing row, which previously let a
        // caller overwrite another document's s3Key/entityId/description by
        // just naming its id. Every confirm is a brand-new row.
        meta.setId(null);
        meta.setUploadedDate(LocalDateTime.now());
        return documentRepository.save(meta);
    }

    public List<Document> listForEntity(String entityType, Long entityId) {
        requireAccess(entityType, entityId);
        return documentRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    public DownloadLink presignDownload(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not exist with id: " + id));
        requireAccess(doc.getEntityType(), doc.getEntityId());
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketForCurrentTenant())
                .key(doc.getS3Key())
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getRequest)
                .build();
        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        return new DownloadLink(url);
    }

    public void delete(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not exist with id: " + id));
        requireAccess(doc.getEntityType(), doc.getEntityId());
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketForCurrentTenant())
                .key(doc.getS3Key())
                .build());
        documentRepository.delete(doc);
    }
}
