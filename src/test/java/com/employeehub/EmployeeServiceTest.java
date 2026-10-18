package com.employeehub;

import com.employeehub.config.CompanyIdRangeConfig;
import com.employeehub.domain.BasicEmployee;
import com.employeehub.domain.DashboardEmployeeDetails;
import com.employeehub.exception.EmployeeServiceException;
import com.employeehub.model.Employee;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.service.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Employee creation carries the highest data risk in the app: the employeeId
 * is not database-generated, it is picked here out of a company's reserved
 * range. A wrong pick either collides with an existing employee or silently
 * files a hire under the wrong company.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CompanyIdRangeConfig companyIdRangeConfig;

    @InjectMocks
    private EmployeeService employeeService;

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Minimal valid onboarding request; every test overrides only what it is about. */
    private com.employeehub.domain.Employee onboardRequest(String companyName, String status) {
        return new com.employeehub.domain.Employee(
                null,                       // employeeId
                "Software Engineer",        // designation
                "1990-01-01",               // dob
                "123-45-6789",              // ssn
                "1234567890",               // phoneNumber
                "john.doe@example.com",     // emailId
                "Full-time",                // employmentType
                "W2",                       // employeeType
                null,                       // vendorId
                "John",                     // firstName
                "Doe",                      // lastName
                "male",                     // gender
                LocalDate.of(2024, 1, 15),  // startDate
                null,                       // endDate
                "Referrer",                 // referredBy
                "W2",                       // taxTerms
                "H1B",                      // visa
                "123 Main St",              // streetAddress
                null,                       // address_line_2
                "Dallas",                   // city
                "TX",                       // state
                "USA",                      // country
                "75001",                    // zipCode
                status,                     // status
                "Dallas",                   // location
                null,                       // income
                null,                       // expense
                "Authorized",               // everifyStatus
                "signed",                   // i9
                "submitted",                // PAF
                null,                       // insurance
                0f,                         // annualPay
                companyName                 // companyName
        );
    }

    /**
     * A partial-update request: only employeeId and (optionally) firstName are
     * set, every other component is null. That "mostly null" shape is the
     * whole point of EmployeeService#saveEmployee — null means "not supplied",
     * so the existing value must survive.
     */
    private com.employeehub.domain.Employee partialUpdate(Long employeeId, String firstName) {
        return new com.employeehub.domain.Employee(
                employeeId, null, null, null, null, null, null, null, null,
                firstName, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, 0f, null);
    }

    /** As {@link #onboardRequest} but with gender and country controllable. */
    private com.employeehub.domain.Employee onboardRequestWith(String gender, String country) {
        return new com.employeehub.domain.Employee(
                null, "Software Engineer", "1990-01-01", "123-45-6789", "1234567890",
                "john.doe@example.com", "Full-time", "W2", null, "John", "Doe",
                gender, LocalDate.of(2024, 1, 15), null, null, "W2", "H1B",
                "123 Main St", null, "Dallas", "TX", country, "75001", "Active", "Dallas",
                null, null, "Authorized", "signed", "submitted", null, 0f, null);
    }

    private void echoSave() {
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── saveEmployeeDetails: id range assignment ────────────────────────────

    @Test
    @DisplayName("saveEmployeeDetails: an Active Intellan hire gets the first free id in the standard range")
    void saveEmployeeDetails_usesStandardRangeForNonNewHire() throws Exception {
        when(companyIdRangeConfig.getRangeFor("Intellan Technologies LLC")).thenReturn(new long[]{1, 100});
        when(employeeRepository.findUsedIdsInRange(1, 100)).thenReturn(Arrays.asList(1L, 2L, 3L));
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(
                onboardRequest("Intellan Technologies LLC", "Active"));

        assertEquals(4L, saved.orElseThrow().getEmployeeId());
        verify(companyIdRangeConfig, never()).getNewHireRangeFor(any());
    }

    @Test
    @DisplayName("saveEmployeeDetails: status 'newHires' routes the id to the company's new-hire range")
    void saveEmployeeDetails_usesNewHireRange() throws Exception {
        when(companyIdRangeConfig.getNewHireRangeFor("Code9 LLC")).thenReturn(new long[]{600, 699});
        when(employeeRepository.findUsedIdsInRange(600, 699)).thenReturn(Collections.emptyList());
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(
                onboardRequest("Code9 LLC", "newHires"));

        assertEquals(600L, saved.orElseThrow().getEmployeeId());
    }

    @Test
    @DisplayName("saveEmployeeDetails: the 'newHires' status check ignores case and surrounding whitespace")
    void saveEmployeeDetails_newHireStatusIsCaseAndWhitespaceInsensitive() throws Exception {
        when(companyIdRangeConfig.getNewHireRangeFor("Code9 LLC")).thenReturn(new long[]{600, 699});
        when(employeeRepository.findUsedIdsInRange(600, 699)).thenReturn(Collections.emptyList());
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(
                onboardRequest("Code9 LLC", "  NEWHIRES "));

        assertEquals(600L, saved.orElseThrow().getEmployeeId());
    }

    @Test
    @DisplayName("saveEmployeeDetails: a null status falls back to the standard range, not the new-hire one")
    void saveEmployeeDetails_nullStatusUsesStandardRange() throws Exception {
        when(companyIdRangeConfig.getRangeFor("Code9 LLC")).thenReturn(new long[]{101, 200});
        when(employeeRepository.findUsedIdsInRange(101, 200)).thenReturn(Collections.emptyList());
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(
                onboardRequest("Code9 LLC", null));

        assertEquals(101L, saved.orElseThrow().getEmployeeId());
    }

    @Test
    @DisplayName("saveEmployeeDetails: fills a gap left by a deleted employee rather than appending past the end")
    void saveEmployeeDetails_fillsGapsInRange() throws Exception {
        when(companyIdRangeConfig.getRangeFor("Intellan Technologies LLC")).thenReturn(new long[]{1, 100});
        when(employeeRepository.findUsedIdsInRange(1, 100)).thenReturn(Arrays.asList(1L, 2L, 4L, 5L));
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(
                onboardRequest("Intellan Technologies LLC", "Active"));

        assertEquals(3L, saved.orElseThrow().getEmployeeId());
    }

    @Test
    @DisplayName("saveEmployeeDetails: takes the last id in the range when only it is free")
    void saveEmployeeDetails_takesLastIdInRange() throws Exception {
        when(companyIdRangeConfig.getRangeFor("Referral")).thenReturn(new long[]{900, 902});
        when(employeeRepository.findUsedIdsInRange(900, 902)).thenReturn(Arrays.asList(900L, 901L));
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(
                onboardRequest("Referral", "Active"));

        assertEquals(902L, saved.orElseThrow().getEmployeeId());
    }

    @Test
    @DisplayName("saveEmployeeDetails: throws EmployeeServiceException when the reserved range is full")
    void saveEmployeeDetails_throwsWhenRangeExhausted() {
        when(companyIdRangeConfig.getRangeFor("Referral")).thenReturn(new long[]{900, 902});
        when(employeeRepository.findUsedIdsInRange(900, 902)).thenReturn(Arrays.asList(900L, 901L, 902L));

        EmployeeServiceException ex = assertThrows(EmployeeServiceException.class,
                () -> employeeService.saveEmployeeDetails(onboardRequest("Referral", "Active")));

        assertTrue(ex.getMessage().contains("No available IDs left"));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveEmployeeDetails: an unconfigured company falls back to max existing id + 1")
    void saveEmployeeDetails_fallsBackToMaxIdPlusOneForUnknownCompany() throws Exception {
        when(companyIdRangeConfig.getRangeFor("Unlisted Co")).thenReturn(null);
        when(employeeRepository.findMaxEmployeeId()).thenReturn(7421L);
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(
                onboardRequest("Unlisted Co", "Active"));

        assertEquals(7422L, saved.orElseThrow().getEmployeeId());
    }

    @Test
    @DisplayName("saveEmployeeDetails: with no company at all, falls back to max existing id + 1")
    void saveEmployeeDetails_fallsBackWhenCompanyMissing() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(10L);
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(onboardRequest(null, "Active"));

        assertEquals(11L, saved.orElseThrow().getEmployeeId());
        verify(companyIdRangeConfig, never()).getRangeFor(any());
    }

    @Test
    @DisplayName("saveEmployeeDetails: an empty-string company also falls back to max existing id + 1")
    void saveEmployeeDetails_fallsBackWhenCompanyBlank() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        echoSave();

        Optional<Employee> saved = employeeService.saveEmployeeDetails(onboardRequest("", "Active"));

        assertEquals(1L, saved.orElseThrow().getEmployeeId());
    }

    // ── saveEmployeeDetails: field mapping ──────────────────────────────────

    @Test
    @DisplayName("saveEmployeeDetails: maps the identity, contact and employment fields onto the entity")
    void saveEmployeeDetails_mapsCoreFields() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        echoSave();

        Employee saved = employeeService.saveEmployeeDetails(onboardRequest(null, "Active")).orElseThrow();

        assertEquals("John", saved.getFirstName());
        assertEquals("Doe", saved.getLastName());
        assertEquals("john.doe@example.com", saved.getEmailId());
        assertEquals("1234567890", saved.getPhone());
        assertEquals("Software Engineer", saved.getDesignation());
        assertEquals("123-45-6789", saved.getSsn());
        assertEquals("1990-01-01", saved.getDob());
        assertEquals("Full-time", saved.getEmploymentType());
        assertEquals("W2", saved.getTaxTerm());
        assertEquals("H1B", saved.getVisa());
        assertEquals("Active", saved.getStatus());
        assertEquals("Dallas", saved.getLocation());
        assertEquals(LocalDate.of(2024, 1, 15), saved.getStartDate());
        assertEquals("Referrer", saved.getReferredBy());
        assertEquals("signed", saved.getI9());
        assertEquals("submitted", saved.getPAF());
        assertEquals("Authorized", saved.getEverifyStatus());
    }

    @Test
    @DisplayName("saveEmployeeDetails: normalizes gender to upper case")
    void saveEmployeeDetails_upperCasesGender() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        echoSave();

        Employee saved = employeeService.saveEmployeeDetails(onboardRequest(null, "Active")).orElseThrow();

        assertEquals("MALE", saved.getGender());
    }

    @Test
    @DisplayName("saveEmployeeDetails: carries employeeType through so the C2C tax exemption applies immediately")
    void saveEmployeeDetails_mapsEmployeeType() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        echoSave();

        Employee saved = employeeService.saveEmployeeDetails(onboardRequest(null, "Active")).orElseThrow();

        assertEquals("W2", saved.getEmployeeType());
    }

    @Test
    @DisplayName("saveEmployeeDetails: stamps lastUpdated with today's date")
    void saveEmployeeDetails_stampsLastUpdated() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        echoSave();

        Employee saved = employeeService.saveEmployeeDetails(onboardRequest(null, "Active")).orElseThrow();

        assertEquals(LocalDate.now(), saved.getLastUpdated());
    }

    @Test
    @DisplayName("saveEmployeeDetails: attaches exactly one address, linked back to the employee")
    void saveEmployeeDetails_attachesAddress() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        echoSave();

        Employee saved = employeeService.saveEmployeeDetails(onboardRequest(null, "Active")).orElseThrow();

        assertEquals(1, saved.getAddress().size());
        var address = saved.getAddress().get(0);
        assertEquals("123 Main St", address.getAddress());
        assertEquals("Dallas", address.getCity());
        assertEquals("TX", address.getState());
        assertEquals("75001", address.getZipCode());
        assertEquals("USA", address.getCountry());
        assertSame(saved, address.getEmployee());
    }

    @Test
    @DisplayName("saveEmployeeDetails: wraps a repository failure in EmployeeServiceException")
    void saveEmployeeDetails_wrapsRepositoryFailure() {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        when(employeeRepository.save(any(Employee.class))).thenThrow(new RuntimeException("constraint violation"));

        EmployeeServiceException ex = assertThrows(EmployeeServiceException.class,
                () -> employeeService.saveEmployeeDetails(onboardRequest(null, "Active")));

        assertEquals("Employee data not saved", ex.getMessage());
    }

    // ── saveEmployee (partial update of an existing employee) ───────────────

    @Test
    @DisplayName("saveEmployee: applies the supplied fields to the existing employee")
    void saveEmployee_appliesSuppliedFields() throws Exception {
        Employee existing = new Employee();
        existing.setEmployeeId(42L);
        existing.setFirstName("Old");
        existing.setLastName("Name");
        existing.setLocation("Austin");
        when(employeeRepository.findById(42L)).thenReturn(Optional.of(existing));
        echoSave();

        com.employeehub.domain.Employee update = partialUpdate(42L, "New");

        Employee saved = employeeService.saveEmployee(update).orElseThrow();

        assertEquals("New", saved.getFirstName());
    }

    @Test
    @DisplayName("saveEmployee: leaves fields absent from the request untouched (partial update)")
    void saveEmployee_leavesOmittedFieldsUntouched() throws Exception {
        Employee existing = new Employee();
        existing.setEmployeeId(42L);
        existing.setFirstName("Old");
        existing.setLocation("Austin");
        when(employeeRepository.findById(42L)).thenReturn(Optional.of(existing));
        echoSave();

        com.employeehub.domain.Employee update = partialUpdate(42L, "New");

        Employee saved = employeeService.saveEmployee(update).orElseThrow();

        assertEquals("Austin", saved.getLocation(), "location was not in the request, so it must survive");
    }

    @Test
    @DisplayName("saveEmployee: refreshes lastUpdated on every update")
    void saveEmployee_refreshesLastUpdated() throws Exception {
        Employee existing = new Employee();
        existing.setEmployeeId(42L);
        existing.setLastUpdated(LocalDate.of(2020, 1, 1));
        when(employeeRepository.findById(42L)).thenReturn(Optional.of(existing));
        echoSave();

        com.employeehub.domain.Employee update = partialUpdate(42L, null);

        Employee saved = employeeService.saveEmployee(update).orElseThrow();

        assertEquals(LocalDate.now(), saved.getLastUpdated());
    }

    @Test
    @DisplayName("saveEmployee: throws IllegalArgumentException when the employee does not exist")
    void saveEmployee_throwsWhenMissing() {
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        com.employeehub.domain.Employee update = partialUpdate(999L, null);

        assertThrows(IllegalArgumentException.class, () -> employeeService.saveEmployee(update));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveEmployee: wraps a repository failure in EmployeeServiceException")
    void saveEmployee_wrapsRepositoryFailure() {
        Employee existing = new Employee();
        existing.setEmployeeId(42L);
        when(employeeRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(any(Employee.class))).thenThrow(new RuntimeException("db down"));

        com.employeehub.domain.Employee update = partialUpdate(42L, null);

        assertThrows(EmployeeServiceException.class, () -> employeeService.saveEmployee(update));
    }

    // ── read paths ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEmpListByStatus: projects each employee onto the dashboard summary shape")
    void getEmpListByStatus_projectsDashboardFields() {
        Employee emp = new Employee();
        emp.setFirstName("Ada");
        emp.setLastName("Lovelace");
        emp.setGender("FEMALE");
        emp.setDesignation("Engineer");
        emp.setLocation("Dallas");
        emp.setPrimarySkills("Java");
        when(employeeRepository.getEmpListByStatus("Active")).thenReturn(List.of(emp));

        List<DashboardEmployeeDetails> result = employeeService.getEmpListByStatus("Active").orElseThrow();

        assertEquals(1, result.size());
        assertEquals("Ada", result.get(0).firstName());
        assertEquals("Engineer", result.get(0).designation());
    }

    @Test
    @DisplayName("getEmpListByStatus: returns an empty list, not empty Optional, when no employee matches")
    void getEmpListByStatus_emptyForNoMatches() {
        when(employeeRepository.getEmpListByStatus("Bench")).thenReturn(Collections.emptyList());

        Optional<List<DashboardEmployeeDetails>> result = employeeService.getEmpListByStatus("Bench");

        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    @DisplayName("getEmployees: maps the id/first/last projection rows onto BasicEmployee")
    void getEmployees_mapsProjectionRows() {
        when(employeeRepository.getEmployee()).thenReturn(List.of(
                new Object[]{BigInteger.valueOf(1), "Ada", "Lovelace"},
                new Object[]{BigInteger.valueOf(2), "Alan", "Turing"}));

        List<BasicEmployee> result = employeeService.getEmployees();

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getEmployeeId());
        assertEquals("Ada", result.get(0).getName());
        assertEquals("Turing", result.get(1).getStatus());
    }

    @Test
    @DisplayName("getEmployees: returns an empty list when there are no employees")
    void getEmployees_emptyWhenNoRows() {
        when(employeeRepository.getEmployee()).thenReturn(Collections.emptyList());

        assertTrue(employeeService.getEmployees().isEmpty());
    }

    // ── known defects (documented, not fixed) ───────────────────────────────

    @Test
    @DisplayName("saveEmployeeDetails: rejects an onboarding request with no gender")
    void saveEmployeeDetails_nullGenderThrows() {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);

        com.employeehub.domain.Employee request = onboardRequestWith(null, "USA");

        assertThrows(com.employeehub.exception.BadRequestException.class,
                () -> employeeService.saveEmployeeDetails(request));
    }

    @Test
    @DisplayName("saveEmployeeDetails: rejects an onboarding request with no country")
    void saveEmployeeDetails_nullCountryThrows() {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);

        com.employeehub.domain.Employee request = onboardRequestWith("male", null);

        assertThrows(com.employeehub.exception.BadRequestException.class,
                () -> employeeService.saveEmployeeDetails(request));
    }

    @Test
    @DisplayName("saveEmployeeDetails: populates workCity from the request")
    void saveEmployeeDetails_mapsWorkCity() throws Exception {
        when(employeeRepository.findMaxEmployeeId()).thenReturn(0L);
        echoSave();

        Employee saved = employeeService.saveEmployeeDetails(onboardRequest(null, "Active")).orElseThrow();

        assertEquals("Dallas", saved.getWorkCity());
    }
}
