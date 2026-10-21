package com.employeehub;

import com.employeehub.exception.I9ValidationException;
import com.employeehub.i9.I9Rules;
import com.employeehub.model.Document;
import com.employeehub.model.Employee;
import com.employeehub.model.Passport;
import com.employeehub.model.i9.I9Record;
import com.employeehub.repository.DocumentRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.I9RecordRepository;
import com.employeehub.repository.PassportRepository;
import com.employeehub.service.I9RecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Covers what I9RulesTest can't — scenario 30b (E22, which needs this
 * record's attachments, not just its own fields) — plus buildContext's
 * Employee/Passport wiring and the save() validate-then-persist path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class I9RecordServiceTest {

    @Mock
    private I9RecordRepository i9RecordRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PassportRepository passportRepository;
    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private I9RecordService i9RecordService;

    private Employee employee(String status, String visa) {
        Employee e = new Employee();
        e.setEmployeeId(1L);
        e.setFirstName("Jane");
        e.setLastName("Doe");
        e.setStatus(status);
        e.setVisa(visa);
        e.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        return e;
    }

    @Test
    void buildContextReadsEmployeeAndLatestPassport() {
        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(employee("Active", "H1B")));
        Passport older = new Passport();
        older.setPassportNumber("OLD");
        older.setExpiryDate(java.time.LocalDate.of(2025, 1, 1));
        Passport newer = new Passport();
        newer.setPassportNumber("NEW");
        newer.setExpiryDate(java.time.LocalDate.of(2031, 1, 1));
        when(passportRepository.findByEmployee_EmployeeId(1L)).thenReturn(List.of(older, newer));

        I9Rules.EmployeeContext ctx = i9RecordService.buildContext(1L);

        assertEquals("NEW", ctx.passportNumber());
        assertEquals(java.time.LocalDate.of(2031, 1, 1), ctx.passportExpiry());
        assertEquals("H1B", ctx.visaStatus());
    }

    // Scenario 30b: remote exam ticked, enrolled, but no document copies
    // uploaded under this record yet -> E22. This can only be checked once
    // the record has an id (attachments point back at entityId), so it's
    // service-layer behavior, not pure I9Rules.
    @Test
    void remoteExamWithoutCopiesBlocksOnExistingRecord() {
        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(employee("Active", "Citizen")));
        when(passportRepository.findByEmployee_EmployeeId(1L)).thenReturn(Collections.emptyList());
        when(i9RecordRepository.findByEmployeeIdOrderByValidFromDesc(1L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByEntityTypeAndEntityId("I9Record", 5L)).thenReturn(Collections.emptyList());

        I9Record record = new I9Record();
        record.setId(5L);
        record.setEmployeeId(1L);
        record.setSubtype(I9Rules.SubType.NEW_HIRE.name());
        record.setValidFrom(java.time.LocalDate.of(2026, 1, 1));
        record.setRemoteExam(true);
        record.setDocOption(I9Rules.DocOption.LIST_A.name());
        record.getListA().setCode(I9Rules.A_US_PASSPORT);
        record.getListA().setNumber("P1");
        record.getListA().setExpiry(java.time.LocalDate.of(2030, 1, 1));

        I9Rules.ValidationResult result = i9RecordService.validate(record);
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E22")));
    }

    @Test
    void remoteExamWithCopiesUploadedPasses() {
        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(employee("Active", "Citizen")));
        when(passportRepository.findByEmployee_EmployeeId(1L)).thenReturn(Collections.emptyList());
        when(i9RecordRepository.findByEmployeeIdOrderByValidFromDesc(1L)).thenReturn(Collections.emptyList());

        Document copy = new Document();
        copy.setDocumentType("Form I9");
        copy.setSubType("List A copy");
        when(documentRepository.findByEntityTypeAndEntityId("I9Record", 5L)).thenReturn(List.of(copy));

        I9Record record = new I9Record();
        record.setId(5L);
        record.setEmployeeId(1L);
        record.setSubtype(I9Rules.SubType.NEW_HIRE.name());
        record.setValidFrom(java.time.LocalDate.of(2026, 1, 1));
        record.setRemoteExam(true);
        record.setDocOption(I9Rules.DocOption.LIST_A.name());
        record.getListA().setCode(I9Rules.A_US_PASSPORT);
        record.getListA().setNumber("P1");
        record.getListA().setExpiry(java.time.LocalDate.of(2030, 1, 1));

        I9Rules.ValidationResult result = i9RecordService.validate(record);
        assertTrue(result.errors().stream().noneMatch(f -> f.code().equals("E22") || f.code().equals("E27")));
    }

    @Test
    void saveThrowsOnInvalidRecordAndDoesNotPersist() {
        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(employee("Active", "Citizen")));
        when(passportRepository.findByEmployee_EmployeeId(1L)).thenReturn(Collections.emptyList());
        when(i9RecordRepository.findByEmployeeIdOrderByValidFromDesc(1L)).thenReturn(Collections.emptyList());

        I9Record invalid = new I9Record(); // no subtype at all -> E1
        invalid.setEmployeeId(1L);

        assertThrows(I9ValidationException.class, () -> i9RecordService.save(invalid));
    }
}
