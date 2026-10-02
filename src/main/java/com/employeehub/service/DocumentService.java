package com.employeehub.service;

import com.employeehub.config.TenantContext;
import com.employeehub.domain.DownloadLink;
import com.employeehub.domain.PresignedUpload;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Coi;
import com.employeehub.model.Document;
import com.employeehub.model.Project;
import com.employeehub.model.Wage;
import com.employeehub.repository.CoiRepository;
import com.employeehub.repository.DocumentRepository;
import com.employeehub.repository.ProjectRepository;
import com.employeehub.repository.WageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private WageRepository wageRepository;
    @Autowired
    private CoiRepository coiRepository;
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

    public PresignedUpload presignUpload(String entityType, Long entityId, String fileName, String contentType) {
        RootFolder root = resolveRootFolder(entityType, entityId);
        String s3Key = root.prefix() + "/" + root.id() + "/" + entityType.toLowerCase() + "/" + entityId + "/"
                + UUID.randomUUID() + "-" + fileName;
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

    public Document confirm(Document meta) {
        meta.setUploadedDate(LocalDateTime.now());
        return documentRepository.save(meta);
    }

    public List<Document> listForEntity(String entityType, Long entityId) {
        return documentRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    public DownloadLink presignDownload(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not exist with id: " + id));
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
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketForCurrentTenant())
                .key(doc.getS3Key())
                .build());
        documentRepository.delete(doc);
    }
}
