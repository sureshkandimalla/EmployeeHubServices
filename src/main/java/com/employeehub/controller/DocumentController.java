package com.employeehub.controller;

import com.employeehub.domain.DownloadLink;
import com.employeehub.domain.PresignedUpload;
import com.employeehub.model.Document;
import com.employeehub.repository.DocumentRepository;
import com.employeehub.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Generic attachment API — entityType/entityId is the only thing that
// varies across use sites (Insurance today; Customer MSAs, Project POs,
// Employee docs are the same shape). Upload is two calls: presign (get an
// S3 PUT url), then the browser PUTs the file straight to S3, then
// confirm (record the metadata row) once that PUT succeeds.
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentRepository documentRepository;

    // documentType/subType/validFrom/validUntil are optional and only
    // informational here (not yet stored — confirm() is what actually
    // persists them) — they're passed at this step purely so the S3 key
    // can be built as a readable name instead of a random one.
    public record PresignRequest(String entityType, Long entityId, String fileName, String contentType,
                                  String documentType, String subType, String validFrom, String validUntil) {
    }

    @PostMapping("/presign")
    public ResponseEntity<PresignedUpload> presign(@RequestBody PresignRequest request) {
        PresignedUpload upload = documentService.presignUpload(
                request.entityType(), request.entityId(), request.fileName(), request.contentType(),
                request.documentType(), request.subType(), request.validFrom(), request.validUntil());
        return ResponseEntity.ok(upload);
    }

    @PostMapping
    public ResponseEntity<Document> confirm(@RequestBody Document document) {
        return new ResponseEntity<>(documentService.confirm(document), HttpStatus.CREATED);
    }

    // entityId omitted -> every document of that type, e.g. a grid's own
    // "which rows already have a document" indicator, one call for the
    // whole page instead of one per row.
    @GetMapping
    public ResponseEntity<List<Document>> list(
            @RequestParam String entityType, @RequestParam(required = false) Long entityId) {
        List<Document> documents = entityId != null
                ? documentService.listForEntity(entityType, entityId)
                : documentRepository.findByEntityType(entityType);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}/downloadUrl")
    public ResponseEntity<DownloadLink> downloadUrl(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.presignDownload(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
