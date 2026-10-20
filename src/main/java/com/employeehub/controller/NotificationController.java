package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Manual trigger for the scheduled expiration check — ADMIN-only, exists so
// the daily @Scheduled job (see NotificationService) can be tested or
// re-run on demand without waiting for 7am. There is deliberately no
// generic "send an email" endpoint here (see EmailService's class comment)
// — only named, server-controlled triggers like this one.
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/run-expiration-check")
    public ResponseEntity<String> runExpirationCheck() {
        CurrentUserContext.requireRole("ADMIN");
        notificationService.checkExpiringDocuments();
        return ResponseEntity.ok("Expiration check complete.");
    }
}
