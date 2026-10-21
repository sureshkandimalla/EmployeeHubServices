package com.employeehub.service;

import com.employeehub.i9.I9Rules;
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
import java.util.stream.Stream;

// Domain-specific notification triggers, each built on the generic
// EmailService primitive — this is the one place that knows *what* to say
// and *who* HR/onboarding/immigration actually are (everyone with role=HR
// or role=IMMIGRATION in user_role); EmailService only knows how to send a
// message it's handed. Adding a third trigger later (visa status change,
// overdue invoice, ...) means adding one more method here, not touching
// EmailService.
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
    @Autowired
    private I9RecordService i9RecordService;

    @Value("${app.mail.expiration-lead-days:30}")
    private int expirationLeadDays;

    // No dedicated "onboarding" role exists today (see UserRole: ADMIN |
    // HR | ACCOUNTING | IMMIGRATION) — onboarding notifications go to the
    // same HR list, plus Immigration (new hires and expiring documents are
    // both squarely their concern too — visa status, EAD/passport renewals,
    // etc.). If a distinct onboarding distribution is ever needed, this is
    // the one place to change.
    private List<String> hrAndImmigrationRecipients() {
        return Stream.of("HR", "IMMIGRATION")
                .flatMap(role -> userRoleRepository.findByRoleIgnoreCase(role).stream())
                .map(r -> r.getEmail())
                .distinct()
                .collect(Collectors.toList());
    }

    public void notifyNewEmployeeCreated(Employee employee) {
        List<String> recipients = hrAndImmigrationRecipients();
        if (recipients.isEmpty()) {
            logger.warn("New-employee email for {} {} skipped: no one has the HR or IMMIGRATION role",
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

        List<String> recipients = hrAndImmigrationRecipients();
        if (recipients.isEmpty()) {
            logger.warn("{} expiring document(s) found but no one has the HR or IMMIGRATION role — not marking "
                    + "as alerted, will retry next run", expiring.size());
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

    // Runs daily (offset from checkExpiringDocuments by 30 minutes so the
    // two jobs don't start in the same minute). Unlike checkExpiringDocuments
    // this has no per-item dedupe — it's a recurring digest of whatever is
    // currently High priority (section 8's own open-tasks list, computed
    // fresh each run via I9Rules), not a one-time alert per newly-expiring
    // item. That's a deliberate simplification: I9Rules.openTasks has no
    // persisted "already alerted" state, and adding one would mean a new
    // table just to suppress re-sending the same still-open task — a HR
    // inbox getting the same open item again tomorrow is an acceptable
    // trade-off against that added complexity.
    @Scheduled(cron = "0 30 7 * * *")
    public void checkI9Compliance() {
        List<I9Rules.Task> highPriority = i9RecordService.openTasksForAllEmployees().stream()
                .filter(t -> t.priority() == I9Rules.Priority.HIGH)
                .collect(Collectors.toList());

        if (highPriority.isEmpty()) {
            logger.debug("I-9 compliance check: no High-priority open tasks");
            return;
        }

        List<String> recipients = hrAndImmigrationRecipients();
        if (recipients.isEmpty()) {
            logger.warn("{} High-priority I-9 task(s) found but no one has the HR or IMMIGRATION role",
                    highPriority.size());
            return;
        }

        Map<Long, Employee> employeesById = employeeRepository.findAllById(
                highPriority.stream().map(I9Rules.Task::employeeId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Employee::getEmployeeId, e -> e));

        StringBuilder rows = new StringBuilder();
        for (I9Rules.Task task : highPriority) {
            Employee emp = employeesById.get(task.employeeId());
            String employeeName = emp != null ? emp.getFirstName() + " " + emp.getLastName()
                    : "Employee #" + task.employeeId();
            rows.append("<tr>")
                    .append("<td style=\"padding:4px 12px 4px 0\">").append(HtmlUtils.htmlEscape(employeeName)).append("</td>")
                    .append("<td style=\"padding:4px 12px 4px 0\">").append(HtmlUtils.htmlEscape(task.label())).append("</td>")
                    .append("<td style=\"padding:4px 12px 4px 0\">")
                    .append(task.due() != null ? task.due().format(DATE_FMT) : "")
                    .append("</td>")
                    .append("<td style=\"padding:4px 12px 4px 0\">").append(HtmlUtils.htmlEscape(task.whereToFix())).append("</td>")
                    .append("</tr>");
        }

        String subject = highPriority.size() == 1
                ? "1 I-9 compliance task needs attention"
                : highPriority.size() + " I-9 compliance tasks need attention";
        String body = "<p>The following High-priority I-9 compliance tasks are currently open:</p>"
                + "<table cellpadding=\"4\" style=\"border-collapse:collapse\">"
                + "<tr><th align=\"left\">Employee</th><th align=\"left\">Task</th><th align=\"left\">Due</th>"
                + "<th align=\"left\">Where to fix</th></tr>"
                + rows
                + "</table>";

        emailService.send(recipients, subject, body);
    }

    private String row(String label, String value) {
        if (value == null || value.isEmpty()) return "";
        return "<tr><td style=\"color:#666;padding:2px 12px 2px 0\">" + HtmlUtils.htmlEscape(label) + "</td>"
                + "<td>" + HtmlUtils.htmlEscape(value) + "</td></tr>";
    }
}
