package com.employeehub.service;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.I9ValidationException;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.i9.I9Config;
import com.employeehub.i9.I9PdfExtraction;
import com.employeehub.i9.I9PdfExtractor;
import com.employeehub.i9.I9Rules;
import com.employeehub.model.Document;
import com.employeehub.model.Employee;
import com.employeehub.model.Passport;
import com.employeehub.model.i9.I9Record;
import com.employeehub.repository.DocumentRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.I9RecordRepository;
import com.employeehub.repository.PassportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// Orchestrates I9Rules against persistence — loads an employee's records,
// builds the EmployeeContext (hire/exit/visa from Employee, passport from
// the existing Passport table per the project's decision to reuse it
// rather than duplicate passport data into I9Record), and always
// re-validates server-side before saving (I9RecordController never trusts
// whatever the client computed, per the spec's "the backend must
// re-validate" instruction).
@Service
public class I9RecordService {

    @Autowired
    private I9RecordRepository i9RecordRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PassportRepository passportRepository;
    @Autowired
    private DocumentRepository documentRepository;

    public record RecordView(I9Record record, String recordId, LocalDate section2Due, LocalDate everifyDue,
                              LocalDate reverifyBy, String reverifySource, boolean reverifyNotRequired,
                              String suggestedNote, List<I9Rules.Finding> warnings) {
    }

    public record EmployeeSummary(Long employeeId, String status, LocalDate dueDate, LocalDate retainUntil,
                                   List<RecordView> records) {
    }

    public I9Rules.EmployeeContext buildContext(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + employeeId));
        Passport passport = passportRepository.findByEmployee_EmployeeId(employeeId).stream()
                .max(Comparator.comparing(Passport::getExpiryDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        return new I9Rules.EmployeeContext(employeeId, employee.getStartDate(), employee.getEndDate(),
                employee.getStatus(), employee.getVisa(),
                passport != null ? passport.getPassportNumber() : null,
                passport != null ? passport.getExpiryDate() : null);
    }

    public EmployeeSummary getForEmployee(Long employeeId) {
        LocalDate today = LocalDate.now();
        List<I9Record> records = i9RecordRepository.findByEmployeeIdOrderByValidFromDesc(employeeId);
        I9Rules.EmployeeContext ctx = buildContext(employeeId);
        I9Rules.EmployeeStatusResult status = I9Rules.employeeI9Status(ctx, records, today);
        LocalDate retainUntil = I9Rules.retainUntil(ctx.hireDate(), ctx.lastDay());

        List<RecordView> views = records.stream().map(r -> toView(r, records)).collect(Collectors.toList());
        return new EmployeeSummary(employeeId, status.status(), status.dueDate(), retainUntil, views);
    }

    private RecordView toView(I9Record record, List<I9Record> allRecords) {
        I9Record effective = I9Rules.effectiveRecord(record, allRecords);
        LocalDate section2Due = I9Rules.section2Due(effective, buildContext(record.getEmployeeId()), null);
        LocalDate everifyDue = I9Rules.everifyDue(effective, buildContext(record.getEmployeeId()));
        I9Rules.ReverifyResult reverify = I9Rules.reverifyBy(effective, allRecords);
        String note = I9Rules.suggestedNote(effective);
        String recordId = String.format("%d-I%02d", record.getEmployeeId(), record.getRecordNo());
        return new RecordView(record, recordId, section2Due, everifyDue, reverify.date(), reverify.source(),
                reverify.notRequired(), note, List.of());
    }

    // Validates without persisting — used by the frontend's inline-error
    // flow before Upload is enabled, and by save() itself.
    public I9Rules.ValidationResult validate(I9Record incoming) {
        List<I9Record> existing = i9RecordRepository.findByEmployeeIdOrderByValidFromDesc(incoming.getEmployeeId());
        I9Rules.EmployeeContext ctx = buildContext(incoming.getEmployeeId());
        I9Rules.ValidationResult result = I9Rules.validate(incoming, ctx, existing);
        return appendAttachmentErrors(incoming, result);
    }

    // E22 (remote-exam document copies) and E23 (Supplement A file) need
    // this record's attachments, which only exist once it already has an
    // id (Document rows point back at entityId=I9Record.id) — so these are
    // checked here, against whatever's already been uploaded under this
    // record, rather than inside the framework-free I9Rules module.
    private I9Rules.ValidationResult appendAttachmentErrors(I9Record incoming, I9Rules.ValidationResult result) {
        if (incoming.getId() == null) return result;
        List<Document> files = documentRepository.findByEntityTypeAndEntityId("I9Record", incoming.getId());
        List<I9Rules.Finding> errors = new ArrayList<>(result.errors());

        if (Boolean.TRUE.equals(incoming.getRemoteExam())) {
            boolean hasCopies = files.stream().anyMatch(f ->
                    "List A copy".equals(f.getSubType()) || "List B copy".equals(f.getSubType())
                            || "List C copy".equals(f.getSubType()));
            if (!hasCopies) {
                errors.add(new I9Rules.Finding("E22", "Upload copies of every document examined remotely"));
            }
        }
        if (Boolean.TRUE.equals(incoming.getPreparerUsed())) {
            boolean hasSupplementA = files.stream().anyMatch(f -> "Supplement A".equals(f.getSubType()));
            if (!hasSupplementA) {
                errors.add(new I9Rules.Finding("E23", "Upload Supplement A"));
            }
        }
        boolean needsFiles = !"DESTROYED".equals(incoming.getSubtype()) && !"MIGRATED".equals(incoming.getSubtype());
        if (needsFiles && files.isEmpty()) {
            errors.add(new I9Rules.Finding("E27", "Add at least one file"));
        }
        return new I9Rules.ValidationResult(errors, result.warnings());
    }

    public I9Record save(I9Record incoming) {
        I9Rules.ValidationResult result = validate(incoming);
        if (!result.isValid()) {
            throw new I9ValidationException(result);
        }
        if (incoming.getId() == null) {
            incoming.setRecordNo(nextRecordNo(incoming.getEmployeeId()));
            incoming.setCreatedBy(CurrentUserContext.email());
            incoming.setCreatedAt(LocalDateTime.now());
        }
        return i9RecordRepository.save(incoming);
    }

    // Never reused, even after a delete — see I9Record's javadoc.
    private int nextRecordNo(Long employeeId) {
        return i9RecordRepository.findByEmployeeIdOrderByValidFromDesc(employeeId).stream()
                .map(I9Record::getRecordNo)
                .filter(n -> n != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    public List<I9Rules.Task> openTasksForAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        List<Long> employeeIds = employees.stream().map(Employee::getEmployeeId).collect(Collectors.toList());
        var recordsByEmployee = i9RecordRepository.findByEmployeeIdInOrderByValidFromDesc(employeeIds).stream()
                .collect(Collectors.groupingBy(I9Record::getEmployeeId));
        LocalDate today = LocalDate.now();

        List<I9Rules.Task> tasks = new ArrayList<>();
        for (Employee employee : employees) {
            I9Rules.EmployeeContext ctx = buildContext(employee.getEmployeeId());
            List<I9Record> records = recordsByEmployee.getOrDefault(employee.getEmployeeId(), List.of());
            tasks.addAll(I9Rules.openTasks(ctx, records, today));
        }
        return tasks;
    }

    public record PdfExtractionResult(I9PdfExtraction extraction, boolean nameMismatch, String employeeNameOnFile) {
    }

    // Reads the official Form I-9 PDF's own AcroForm fields (see
    // I9PdfExtractor) and cross-checks the name on the PDF against the
    // employee this record is being added for — a wrong-employee upload
    // (the most dangerous mistake here, given the SSN/A-number involved)
    // surfaces as a warning instead of silently pre-filling the wrong
    // person's data into this employee's record.
    public PdfExtractionResult extractFromPdf(Long employeeId, InputStream pdfStream) throws IOException {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not exist with id :" + employeeId));
        I9PdfExtraction extraction = I9PdfExtractor.extract(pdfStream);

        String employeeName = (employee.getFirstName() + " " + employee.getLastName()).trim();
        boolean nameMismatch = false;
        if (extraction.extractedFirstName() != null || extraction.extractedLastName() != null) {
            String extractedName = ((extraction.extractedFirstName() != null ? extraction.extractedFirstName() : "")
                    + " " + (extraction.extractedLastName() != null ? extraction.extractedLastName() : "")).trim();
            nameMismatch = !normalizeName(extractedName).equals(normalizeName(employeeName));
        }

        return new PdfExtractionResult(extraction, nameMismatch, employeeName);
    }

    private static String normalizeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z]", "");
    }
}
