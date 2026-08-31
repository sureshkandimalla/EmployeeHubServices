package com.employeehub.controller;

import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.ActionRequest;
import com.employeehub.repository.ActionRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

// Records a non-admin's request to Archive/Delete a record, and its
// resolution — see ActionRequest for the full design note. This
// controller never performs the archive/delete itself; approving just
// marks the request resolved, and the frontend then replays the same
// archive/delete call the requesting grid would have made directly.
@RestController
@RequestMapping("/api/v1/actionRequests")
public class ActionRequestController {

    @Autowired
    private ActionRequestRepository actionRequestRepository;

    @GetMapping
    public ResponseEntity<List<ActionRequest>> list(@RequestParam(required = false) String status) {
        List<ActionRequest> requests = status != null
                ? actionRequestRepository.findByStatusOrderByRequestedDateDesc(status)
                : actionRequestRepository.findAllByOrderByRequestedDateDesc();
        return ResponseEntity.ok(requests);
    }

    @PostMapping
    public ResponseEntity<ActionRequest> create(@RequestBody ActionRequest request) {
        request.setRequestId(null); // always a new row — never trust a client-supplied id here
        request.setStatus("PENDING");
        request.setResolvedBy(null);
        request.setResolvedDate(null);
        return new ResponseEntity<>(actionRequestRepository.save(request), HttpStatus.CREATED);
    }

    public record ResolveRequest(String status, String resolvedBy) {
    }

    // status: "APPROVED" or "REJECTED". The caller (PendingRequests.jsx)
    // performs the actual archive/delete itself before calling this for an
    // approval — this just records the outcome.
    @PutMapping("/{requestId}/resolve")
    public ResponseEntity<ActionRequest> resolve(@PathVariable Long requestId, @RequestBody ResolveRequest body) {
        ActionRequest existing = actionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + requestId));
        existing.setStatus(body.status());
        existing.setResolvedBy(body.resolvedBy());
        existing.setResolvedDate(LocalDateTime.now());
        return ResponseEntity.ok(actionRequestRepository.save(existing));
    }
}
