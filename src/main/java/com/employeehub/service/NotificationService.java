package com.employeehub.service;

import com.employeehub.model.Document;
import com.employeehub.model.Employee;
import com.employeehub.repository.DocumentRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Domain-specific notification triggers, each built on the generic
// EmailService primitive — this is the one place that knows *what* to say
// and *who* HR/onboarding actually are (everyone with role=HR in
// user_role); EmailService only knows how to send a message it's handed.
// Adding a third trigger later (visa status change, overdue invoice, ...)
// means adding one more method here, not touching EmailService.
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    @Autowired
    private EmailService emailService;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private EmployeeRepository employeeRepository;

    @Value("${app.mail.expiration-lead-days:30}")
    private int expirationLeadDays;

    // No dedicated "onboarding" role exists today (see UserRole: ADMIN |
    // HR | ACCOUNTING | IMMIGRATION) — onboarding notifications go to the
    // same HR list. If a distinct onboarding distribution is ever needed,
    // this is the one place to change.
    private List<String> hrAndOnboardingRecipients() {
        return userRoleRepository.findByRoleIgnoreCase("HR").stream()
                .map(r -> r.getEmail())
                .collect(Collectors.toList());
    }

    public void notifyNewEmployeeCreated(Employee employee) {
        List<String> recipients = hrAndOnboardingRecipients();
        if (recipients.isEmpty()) {
            logger.warn("New-employee email for {} {} skipped: no one has the HR role",
                    employee.getFirstName(), employee.getLastName());
            return;
        }

        String name = employee.getFirstName() + " " + employee.getLastName();
        String subject = "New employee added: " + name;
        String body = "<p>A new employee has been added to EmployeeHub.</p>"
                + "<table cellpadding=\"4\" style=\"border-collapse:collapse\">"
                + row("Name", name)
                + row("Designation", employee.getDesignation())
                + row("Start Date", employee.getStartDate() != null ? employee.getStartDate().format(DATE_FMT) : null)
                + row("Work Email", employee.getEmailId())
                + "</table>"
                + "<p>Please follow up on onboarding tasks as needed.</p>";

        emailService.send(recipients, subject, body);
    }

    // Runs daily; also callable directly (see NotificationController) to
    // trigger it on demand without waiting for the schedule, e.g. for
    // testing. One digest email per run covering every document that newly
    // entered the lead-time window since the last run — not one email per
    // document, so a batch of expiring docs doesn't flood HR's inbox.
    @Scheduled(cron = "0 0 7 * * *")
    public void checkExpiringDocuments() {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(expirationLeadDays);
        List<Document> expiring = documentRepository
                .findByEntityTypeAndValidUntilBetweenAndExpirationAlertSentAtIsNull("Employee", today, cutoff);

        if (expiring.isEmpty()) {
            logger.debug("Expiration check: nothing newly entering the {}-day window", expirationLeadDays);
            return;
        }

        List<String> recipients = hrAndOnboardingRecipients();
        if (recipients.isEmpty()) {
            logger.warn("{} expiring document(s) found but no one has the HR role — not marking as alerted, "
                    + "will retry next run", expiring.size());
            return;
        }

        Map<Long, Employee> employeesById = employeeRepository.findAllById(
                expiring.stream().map(Document::getEntityId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Employee::getEmployeeId, e -> e));

        StringBuilder rows = new StringBuilder();
        for (Document doc : expiring) {
            Employee emp = employeesById.get(doc.getEntityId());
            String employeeName = emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Employee #" + doc.getEntityId();
            String type = doc.getSubType() != null && !doc.getSubType().isEmpty()
                    ? doc.getDocumentType() + " (" + doc.getSubType() + ")"
                    : doc.getDocumentType();
            rows.append("<tr>")
                    .append("<td style=\"padding:4px 12px 4px 0\">").append(HtmlUtils.htmlEscape(employeeName)).append("</td>")
                    .append("<td style=\"padding:4px 12px 4px 0\">").append(HtmlUtils.htmlEscape(type)).append("</td>")
                    .append("<td style=\"padding:4px 12px 4px 0\">").append(doc.getValidUntil().format(DATE_FMT)).append("</td>")
                    .append("</tr>");
        }

        String subject = expiring.size() == 1
                ? "1 document expiring within " + expirationLeadDays + " days"
                : expiring.size() + " documents expiring within " + expirationLeadDays + " days";
        String body = "<p>The following employee document(s) are expiring within the next "
                + expirationLeadDays + " days:</p>"
                + "<table cellpadding=\"4\" style=\"border-collapse:collapse\">"
                + "<tr><th align=\"left\">Employee</th><th align=\"left\">Document</th><th align=\"left\">Valid Until</th></tr>"
                + rows
                + "</table>";

        emailService.send(recipients, subject, body);

        for (Document doc : expiring) {
            doc.setExpirationAlertSentAt(today);
        }
        documentRepository.saveAll(expiring);
    }

    private String row(String label, String value) {
        if (value == null || value.isEmpty()) return "";
        return "<tr><td style=\"color:#666;padding:2px 12px 2px 0\">" + HtmlUtils.htmlEscape(label) + "</td>"
                + "<td>" + HtmlUtils.htmlEscape(value) + "</td></tr>";
    }
}
