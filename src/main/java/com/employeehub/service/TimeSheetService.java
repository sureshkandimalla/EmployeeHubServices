package com.employeehub.service;

import com.employeehub.model.Assignment;
import com.employeehub.model.Invoice;
import com.employeehub.model.TimeSheet;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.repository.TimeSheetRepository;
import com.employeehub.util.InvoicePeriodCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TimeSheetService {

    public static final String DRAFT = "Draft";
    public static final String SUBMITTED = "Submitted";
    public static final String APPROVED = "Approved";
    public static final String REJECTED = "Rejected";

    @Autowired
    private TimeSheetRepository timeSheetRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    public List<TimeSheet> getByAssignmentAndMonth(Long assignmentId, YearMonth month) {
        return timeSheetRepository.findByAssignmentIdAndWorkDateBetweenOrderByWorkDate(
                assignmentId, month.atDay(1), month.atEndOfMonth());
    }

    public List<TimeSheet> getByEmployeeAndMonth(Long employeeId, YearMonth month) {
        return timeSheetRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(
                employeeId, month.atDay(1), month.atEndOfMonth());
    }

    // Arbitrary date range (not just one month) for an employee — backs the
    // Monthly Timesheets page's per-employee monthly-summary view, which
    // spans a project's whole active period in one fetch.
    public List<TimeSheet> getByEmployeeAndRange(Long employeeId, LocalDate start, LocalDate end) {
        return timeSheetRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(employeeId, start, end);
    }

    // Every entry for the month across all assignments, for the Monthly
    // Timesheets bulk-entry page — one fetch instead of one per assignment.
    public List<TimeSheet> getByMonth(YearMonth month) {
        return timeSheetRepository.findByWorkDateBetweenOrderByWorkDate(month.atDay(1), month.atEndOfMonth());
    }

    // Every entry across all assignments for an arbitrary date range — backs
    // the Payroll Eligibility page, where a pay period rarely lines up with
    // calendar month boundaries (e.g. biweekly).
    public List<TimeSheet> getByRange(LocalDate start, LocalDate end) {
        return timeSheetRepository.findByWorkDateBetweenOrderByWorkDate(start, end);
    }

    // Approved beats Rejected beats Submitted beats Draft — same
    // most-restrictive-wins rule TimesheetEntry.jsx already uses for a
    // single month's status tag, extended with Rejected (not needed there
    // since that page only ever looks at one assignment/month, where a
    // reject immediately becomes actionable as Draft-or-Submitted again).
    private static final List<String> STATUS_PRIORITY = List.of(DRAFT, SUBMITTED, REJECTED, APPROVED);

    private String higherPriorityStatus(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return STATUS_PRIORITY.indexOf(b) > STATUS_PRIORITY.indexOf(a) ? b : a;
    }

    // One row per employee/assignment/month across every timesheet ever
    // entered — backs the Timesheets grid (the landing view of the
    // Timesheets page, with "Add New Timesheet" opening the existing
    // day-by-day entry view for one employee/project/month at a time).
    public List<Map<String, Object>> getTimesheetSummaries() {
        List<Object[]> rows = timeSheetRepository.findAllWithNames();
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();

        for (Object[] r : rows) {
            String firstName = (String) r[0];
            String lastName = (String) r[1];
            Long projectId = ((Number) r[2]).longValue();
            String projectName = (String) r[3];
            Long assignmentId = ((Number) r[4]).longValue();
            Long employeeId = ((Number) r[5]).longValue();
            LocalDate workDate = ((java.sql.Date) r[6]).toLocalDate();
            float hours = r[7] != null ? ((Number) r[7]).floatValue() : 0f;
            String status = (String) r[8];
            LocalDate lastUpdated = r[9] != null ? ((java.sql.Date) r[9]).toLocalDate() : null;
            String employeeEmail = (String) r[10];

            String yearMonth = YearMonth.from(workDate).toString();
            String key = assignmentId + "|" + yearMonth;

            Map<String, Object> group = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("employeeId", employeeId);
                g.put("employeeName", firstName + " " + lastName);
                g.put("employeeEmail", employeeEmail);
                g.put("projectId", projectId);
                g.put("projectName", projectName);
                g.put("assignmentId", assignmentId);
                g.put("yearMonth", yearMonth);
                g.put("totalHours", 0f);
                g.put("status", DRAFT);
                g.put("lastUpdated", null);
                return g;
            });

            group.put("totalHours", (Float) group.get("totalHours") + hours);
            group.put("status", higherPriorityStatus((String) group.get("status"), status));
            LocalDate groupLastUpdated = (LocalDate) group.get("lastUpdated");
            if (lastUpdated != null && (groupLastUpdated == null || lastUpdated.isAfter(groupLastUpdated))) {
                group.put("lastUpdated", lastUpdated);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(grouped.values());
        result.sort((a, b) -> {
            LocalDate la = (LocalDate) a.get("lastUpdated");
            LocalDate lb = (LocalDate) b.get("lastUpdated");
            if (la == null && lb == null) return 0;
            if (la == null) return 1;
            if (lb == null) return -1;
            return lb.compareTo(la);
        });
        return result;
    }

    // Every (employee, assignment, month) that is NOT fully Approved —
    // whether that's because no timesheet exists for that month at all, or
    // one exists but is still Draft/Submitted/Rejected. Walks each
    // assignment's entire start-to-end window (capped at last month, since
    // the current month isn't "late" yet) rather than deriving months from
    // existing timesheet rows the way getTimesheetSummaries does, so a
    // month with zero entries still shows up here.
    public List<Map<String, Object>> getMissingTimesheets() {
        List<Object[]> assignments = assignmentRepository.findAllAssignmentsWithDetails();
        List<Object[]> timesheetRows = timeSheetRepository.findAllWithNames();

        // assignmentId|yearMonth -> most-restrictive-wins status across
        // that month's entries (same rule getTimesheetSummaries uses).
        Map<String, String> statusByKey = new HashMap<>();
        for (Object[] r : timesheetRows) {
            Long assignmentId = ((Number) r[4]).longValue();
            LocalDate workDate = ((java.sql.Date) r[6]).toLocalDate();
            String status = (String) r[8];
            String key = assignmentId + "|" + YearMonth.from(workDate);
            statusByKey.merge(key, status, this::higherPriorityStatus);
        }

        YearMonth lastCompleteMonth = YearMonth.now().minusMonths(1);
        List<Map<String, Object>> missing = new ArrayList<>();

        for (Object[] a : assignments) {
            String firstName = (String) a[0];
            String lastName = (String) a[1];
            Long projectId = ((Number) a[2]).longValue();
            String projectName = (String) a[3];
            Long assignmentId = ((Number) a[4]).longValue();
            Long employeeId = ((Number) a[5]).longValue();
            LocalDate startDate = ((java.sql.Date) a[6]).toLocalDate();
            LocalDate endDate = a[7] != null ? ((java.sql.Date) a[7]).toLocalDate() : null;
            String employeeEmail = (String) a[8];

            YearMonth start = YearMonth.from(startDate);
            YearMonth end = endDate != null ? YearMonth.from(endDate) : lastCompleteMonth;
            if (end.isAfter(lastCompleteMonth)) end = lastCompleteMonth;
            if (start.isAfter(end)) continue; // assignment hasn't reached a completed month yet

            for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                String status = statusByKey.get(assignmentId + "|" + ym);
                if (APPROVED.equals(status)) continue;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("employeeId", employeeId);
                row.put("employeeName", firstName + " " + lastName);
                row.put("employeeEmail", employeeEmail);
                row.put("projectId", projectId);
                row.put("projectName", projectName);
                row.put("assignmentId", assignmentId);
                row.put("yearMonth", ym.toString());
                row.put("status", status == null ? "No Timesheet" : status);
                missing.add(row);
            }
        }

        missing.sort((x, y) -> ((String) y.get("yearMonth")).compareTo((String) x.get("yearMonth")));
        return missing;
    }

    // Mirrors the frontend's INVOICE_TERM_OPTIONS (src/Utils/invoiceTerm.js)
    // — the numeric code is the only thing actually stored, this is purely
    // a display label for this report's response.
    private static final Map<String, String> INVOICE_TERM_LABELS = Map.of(
            "1", "Weekly",
            "2", "Biweekly",
            "3", "Monthly",
            "4", "Special",
            "5", "Semi-Monthly",
            "6", "Once in 4 Weeks");

    // Only Submitted/Approved entries count as "confirmed" hours for this
    // comparison — same convention GenerateInvoiceDetails.jsx's
    // populateHoursFromTimesheets already uses when pulling timesheet hours
    // into a not-yet-invoiced period.
    private static boolean isConfirmedStatus(String status) {
        return SUBMITTED.equals(status) || APPROVED.equals(status);
    }

    // One row per (employee, assignment, invoice period), bucketed by each
    // project's own invoice frequency (Weekly/Biweekly/Monthly/...) instead
    // of a calendar month, with the matching Invoice (if any) attached for
    // direct comparison. Backs the "By Invoice Frequency" tab on the
    // Timesheets page.
    public List<Map<String, Object>> getTimesheetsByInvoiceFrequency() {
        List<Object[]> assignments = assignmentRepository.findAllAssignmentsWithInvoiceTerm();
        List<Object[]> timesheetRows = timeSheetRepository.findAllWithNames();

        // assignmentId -> that assignment's timesheet entries, so each
        // period only has to scan its own assignment's rows, not every
        // timesheet in the system.
        Map<Long, List<Object[]>> timesheetsByAssignment = new HashMap<>();
        for (Object[] r : timesheetRows) {
            Long assignmentId = ((Number) r[4]).longValue();
            timesheetsByAssignment.computeIfAbsent(assignmentId, k -> new ArrayList<>()).add(r);
        }

        // projectId -> that project's invoices, fetched once up front
        // rather than once per assignment.
        Map<Long, List<Invoice>> invoicesByProject = new HashMap<>();
        for (Invoice invoice : invoiceRepository.findAll()) {
            if (invoice.getProjectId() != null) {
                invoicesByProject.computeIfAbsent(invoice.getProjectId(), k -> new ArrayList<>()).add(invoice);
            }
        }

        DateTimeFormatter yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] a : assignments) {
            String firstName = (String) a[0];
            String lastName = (String) a[1];
            Long projectId = ((Number) a[2]).longValue();
            String projectName = (String) a[3];
            Long assignmentId = ((Number) a[4]).longValue();
            Long employeeId = ((Number) a[5]).longValue();
            LocalDate startDate = ((java.sql.Date) a[6]).toLocalDate();
            LocalDate endDate = a[7] != null ? ((java.sql.Date) a[7]).toLocalDate() : null;
            String employeeEmail = (String) a[8];
            String invoiceTerm = (String) a[9];
            DayOfWeek weekStartDay = InvoicePeriodCalculator.resolveWeekStartDay((String) a[10]);

            LocalDate rangeEnd = (endDate == null || endDate.isAfter(today)) ? today : endDate;
            if (startDate.isAfter(rangeEnd)) continue;

            List<InvoicePeriodCalculator.Period> periods = InvoicePeriodCalculator.computePeriods(
                    startDate, rangeEnd, invoiceTerm, weekStartDay);
            List<Object[]> assignmentTimesheets = timesheetsByAssignment.getOrDefault(assignmentId, List.of());
            List<Invoice> projectInvoices = invoicesByProject.getOrDefault(projectId, List.of());

            for (InvoicePeriodCalculator.Period period : periods) {
                // Only periods that have fully ended — an in-progress period
                // hasn't been (and shouldn't yet be) invoiced, so flagging it
                // as "No Invoice Yet" would just be noise.
                if (period.end.isAfter(today)) continue;

                float timesheetHours = 0f;
                String timesheetStatus = null;
                for (Object[] ts : assignmentTimesheets) {
                    LocalDate workDate = ((java.sql.Date) ts[6]).toLocalDate();
                    if (workDate.isBefore(period.start) || workDate.isAfter(period.end)) continue;
                    String status = (String) ts[8];
                    if (isConfirmedStatus(status)) {
                        float hours = ts[7] != null ? ((Number) ts[7]).floatValue() : 0f;
                        timesheetHours += hours;
                    }
                    timesheetStatus = higherPriorityStatus(timesheetStatus, status);
                }

                Optional<Invoice> matched = projectInvoices.stream()
                        .filter(invoice -> period.start.equals(invoice.getStartDate()))
                        .findAny();
                boolean isMonthlyBucketing = !List.of("1", "2", "5", "6").contains(invoiceTerm);
                if (matched.isEmpty() && isMonthlyBucketing) {
                    // Legacy fallback for Monthly (and the Monthly-fallback
                    // "Special"/unrecognized terms) only, matching
                    // InvoiceRepository#findByProjectIdAndInvoiceMonth's own
                    // documented "kept for reference/compat" rule — some
                    // older Monthly invoices predate exact start-date
                    // matching and only have invoiceMonth set correctly.
                    String targetMonth = period.start.format(yearMonthFormatter);
                    matched = projectInvoices.stream()
                            .filter(invoice -> invoice.getInvoiceMonth() != null
                                    && invoice.getInvoiceMonth().format(yearMonthFormatter).equals(targetMonth))
                            .findAny();
                }

                float invoicedHours = matched.map(Invoice::getHours).orElse(0f);
                float variance = timesheetHours - invoicedHours;
                String comparisonStatus;
                if (matched.isEmpty()) {
                    comparisonStatus = timesheetHours > 0f ? "No Invoice Yet" : "Matched";
                } else if (Math.abs(variance) > 0.01f) {
                    comparisonStatus = "Hours Mismatch";
                } else {
                    comparisonStatus = "Matched";
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("employeeId", employeeId);
                row.put("employeeName", firstName + " " + lastName);
                row.put("employeeEmail", employeeEmail);
                row.put("projectId", projectId);
                row.put("projectName", projectName);
                row.put("assignmentId", assignmentId);
                // The month to pre-select when jumping to the day-by-day
                // Timesheet Entry view (TimesheetEntry.jsx only understands
                // a single calendar month) — the period's start date's
                // month, same approximation GenerateInvoiceDetails' own
                // "Timesheet" link already makes for non-Monthly terms.
                row.put("yearMonth", YearMonth.from(period.start).toString());
                row.put("invoiceFrequency", INVOICE_TERM_LABELS.getOrDefault(invoiceTerm, "Monthly"));
                row.put("periodStart", period.start.toString());
                row.put("periodEnd", period.end.toString());
                row.put("timesheetHours", timesheetHours);
                row.put("timesheetStatus", timesheetStatus == null ? "No Timesheet" : timesheetStatus);
                row.put("invoiceId", matched.map(Invoice::getId).orElse(null));
                row.put("invoiceNumber", matched.map(Invoice::getInvoiceNumber).orElse(null));
                row.put("invoicedHours", invoicedHours);
                row.put("invoiceStatus", matched.map(Invoice::getStatus).orElse(null));
                row.put("variance", variance);
                row.put("comparisonStatus", comparisonStatus);
                result.add(row);
            }
        }

        result.sort((x, y) -> ((String) y.get("periodStart")).compareTo((String) x.get("periodStart")));
        return result;
    }

    public static class SaveResult {
        public final List<TimeSheet> saved;
        public final List<LocalDate> lockedDates;
        public final List<LocalDate> outOfRangeDates;

        public SaveResult(List<TimeSheet> saved, List<LocalDate> lockedDates, List<LocalDate> outOfRangeDates) {
            this.saved = saved;
            this.lockedDates = lockedDates;
            this.outOfRangeDates = outOfRangeDates;
        }
    }

    /**
     * Upserts each entry by (assignmentId, workDate). Entries with no hours
     * and no existing row are skipped (no point persisting a zero-hour day
     * nobody worked). Rows currently Approved are locked — they're left
     * untouched and their dates are reported back so the caller can tell the
     * user to reopen the month first. Editing a Draft, Submitted, or
     * Rejected row resets it to Draft, since a submission/rejection no
     * longer matches once the hours are changed.
     * Entries outside the assignment's start/end date range are rejected —
     * the UI already greys those days out, but this is enforced server-side
     * too since saveBulk can be called directly.
     */
    public SaveResult saveBulk(List<TimeSheet> entries) {
        List<TimeSheet> saved = new ArrayList<>();
        List<LocalDate> lockedDates = new ArrayList<>();
        List<LocalDate> outOfRangeDates = new ArrayList<>();
        Map<Long, Assignment> assignmentCache = new HashMap<>();

        for (TimeSheet entry : entries) {
            Assignment assignment = assignmentCache.computeIfAbsent(entry.getAssignmentId(),
                    id -> assignmentRepository.findById(id).orElse(null));

            if (assignment != null && !isWithinAssignmentRange(entry.getWorkDate(), assignment)) {
                outOfRangeDates.add(entry.getWorkDate());
                continue;
            }

            Optional<TimeSheet> existing = timeSheetRepository
                    .findByAssignmentIdAndWorkDate(entry.getAssignmentId(), entry.getWorkDate());

            if (existing.isPresent()) {
                TimeSheet ts = existing.get();
                if (APPROVED.equals(ts.getStatus())) {
                    lockedDates.add(entry.getWorkDate());
                    continue;
                }
                ts.setHours(entry.getHours());
                ts.setNotes(entry.getNotes());
                ts.setStatus(DRAFT);
                saved.add(timeSheetRepository.save(ts));
            } else {
                if (entry.getHours() == null || entry.getHours() <= 0) continue;
                entry.setTimesheetId(null);
                entry.setStatus(DRAFT);
                saved.add(timeSheetRepository.save(entry));
            }
        }

        return new SaveResult(saved, lockedDates, outOfRangeDates);
    }

    private boolean isWithinAssignmentRange(LocalDate workDate, Assignment assignment) {
        if (assignment.getStartDate() != null && workDate.isBefore(assignment.getStartDate())) return false;
        if (assignment.getEndDate() != null && workDate.isAfter(assignment.getEndDate())) return false;
        return true;
    }

    public int submitMonth(Long assignmentId, YearMonth month) {
        return submitRange(assignmentId, month.atDay(1), month.atEndOfMonth());
    }

    public int approveMonth(Long assignmentId, YearMonth month) {
        return approveRange(assignmentId, month.atDay(1), month.atEndOfMonth());
    }

    public int reopenMonth(Long assignmentId, YearMonth month) {
        return reopenRange(assignmentId, month.atDay(1), month.atEndOfMonth());
    }

    // Range-scoped versions of the same actions — used by the Monthly
    // Timesheets page's per-week action menu (a week is just a date range
    // that isn't necessarily a whole month).
    public int submitRange(Long assignmentId, LocalDate start, LocalDate end) {
        return updateStatusForRange(assignmentId, start, end, DRAFT, SUBMITTED);
    }

    public int approveRange(Long assignmentId, LocalDate start, LocalDate end) {
        return updateStatusForRange(assignmentId, start, end, SUBMITTED, APPROVED);
    }

    public int rejectRange(Long assignmentId, LocalDate start, LocalDate end) {
        return updateStatusForRange(assignmentId, start, end, SUBMITTED, REJECTED);
    }

    public int reopenRange(Long assignmentId, LocalDate start, LocalDate end) {
        List<TimeSheet> entries = timeSheetRepository
                .findByAssignmentIdAndWorkDateBetweenOrderByWorkDate(assignmentId, start, end);
        int updated = 0;
        for (TimeSheet ts : entries) {
            String status = ts.getStatus();
            if (SUBMITTED.equals(status) || APPROVED.equals(status) || REJECTED.equals(status)) {
                ts.setStatus(DRAFT);
                timeSheetRepository.save(ts);
                updated++;
            }
        }
        return updated;
    }

    private int updateStatusForRange(Long assignmentId, LocalDate start, LocalDate end, String fromStatus, String toStatus) {
        List<TimeSheet> entries = timeSheetRepository
                .findByAssignmentIdAndWorkDateBetweenOrderByWorkDate(assignmentId, start, end);
        int updated = 0;
        for (TimeSheet ts : entries) {
            if (fromStatus.equals(ts.getStatus())) {
                ts.setStatus(toStatus);
                timeSheetRepository.save(ts);
                updated++;
            }
        }
        return updated;
    }
}
