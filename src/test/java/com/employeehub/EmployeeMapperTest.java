package com.employeehub;

import com.employeehub.model.Address;
import com.employeehub.model.Employee;
import com.employeehub.service.EmployeeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmployeeMapper sits between the JPA entity and the API/DTO shape. Anything
 * it drops silently disappears from a response, so the mapping is pinned in
 * both directions.
 */
class EmployeeMapperTest {

    private Employee modelEmployee() {
        Employee e = new Employee();
        e.setEmployeeId(42L);
        e.setFirstName("Ada");
        e.setLastName("Lovelace");
        e.setEmailId("ada@example.com");
        e.setPhone("5551234567");
        e.setDob("1990-01-01");
        e.setSsn("123-45-6789");
        e.setStartDate(LocalDate.of(2024, 1, 15));
        e.setEndDate(LocalDate.of(2026, 1, 14));
        e.setDesignation("Engineer");
        e.setEmploymentType("Full-Time");
        e.setEmployeeType("W2");
        e.setVendorId(8L);
        e.setStatus("Active");
        e.setLocation("Dallas");
        e.setCompanyName("Intellan Technologies LLC");
        e.setGender("FEMALE");
        e.setReferredBy("Alan");
        e.setTaxTerm("W2");
        e.setVisa("H1B");
        e.setEverifyStatus("Completed");
        e.setI9("Completed");
        e.setPAF("submitted");
        e.setInsurance("Aetna");
        e.setAnnualPay(120000f);

        Address address = new Address();
        address.setAddress("123 Main St");
        address.setCity("Dallas");
        List<Address> addresses = new ArrayList<>();
        addresses.add(address);
        e.setAddress(addresses);
        return e;
    }

    private com.employeehub.domain.Employee domainEmployee(Long id, String companyName) {
        return new com.employeehub.domain.Employee(
                id, "Engineer", "1990-01-01", "123-45-6789", "5551234567", "ada@example.com",
                "Full-Time", "W2", 8L, "Ada", "Lovelace", "FEMALE",
                LocalDate.of(2024, 1, 15), LocalDate.of(2026, 1, 14), "Alan", "W2", "H1B",
                "123 Main St", null, "Dallas", "TX", "USA", "75001", "Active", "Dallas",
                null, null, "Completed", "Completed", "submitted", "Aetna", 120000f, companyName);
    }

    // ── mapToModel ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("mapToModel: copies the identity and contact fields across")
    void mapToModel_copiesIdentityAndContact() {
        Employee model = EmployeeMapper.mapToModel(domainEmployee(42L, "Intellan Technologies LLC"));

        assertEquals(42L, model.getEmployeeId());
        assertEquals("Ada", model.getFirstName());
        assertEquals("Lovelace", model.getLastName());
        assertEquals("ada@example.com", model.getEmailId());
        assertEquals("5551234567", model.getPhone());
        assertEquals("1990-01-01", model.getDob());
    }

    @Test
    @DisplayName("mapToModel: copies the employment fields across")
    void mapToModel_copiesEmploymentFields() {
        Employee model = EmployeeMapper.mapToModel(domainEmployee(42L, "Intellan Technologies LLC"));

        assertEquals(LocalDate.of(2024, 1, 15), model.getStartDate());
        assertEquals(LocalDate.of(2026, 1, 14), model.getEndDate());
        assertEquals("Engineer", model.getDesignation());
        assertEquals("Full-Time", model.getEmploymentType());
        assertEquals("W2", model.getEmployeeType());
        assertEquals(8L, model.getVendorId());
        assertEquals("Active", model.getStatus());
        assertEquals("Dallas", model.getLocation());
        assertEquals("Intellan Technologies LLC", model.getCompanyName());
    }

    @Test
    @DisplayName("mapToModel: tolerates a request with no company name")
    void mapToModel_nullCompanyName() {
        Employee model = EmployeeMapper.mapToModel(domainEmployee(42L, null));

        assertNull(model.getCompanyName());
    }

    @Test
    @DisplayName("mapToModelList: maps every element and preserves order")
    void mapToModelList_mapsEveryElement() {
        List<Employee> models = EmployeeMapper.mapToModelList(List.of(
                domainEmployee(1L, "Intellan Technologies LLC"),
                domainEmployee(2L, "Code9 LLC")));

        assertEquals(2, models.size());
        assertEquals(1L, models.get(0).getEmployeeId());
        assertEquals("Code9 LLC", models.get(1).getCompanyName());
    }

    @Test
    @DisplayName("mapToModelList: returns an empty list for an empty input")
    void mapToModelList_emptyInput() {
        assertTrue(EmployeeMapper.mapToModelList(List.of()).isEmpty());
    }

    // ── mapToDomain ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("mapToDomain: copies the identity and contact fields across")
    void mapToDomain_copiesIdentityAndContact() {
        com.employeehub.domain.Employee domain = EmployeeMapper.mapToDomain(modelEmployee(), 1000L, 200L);

        assertEquals(42L, domain.employeeId());
        assertEquals("Ada", domain.firstName());
        assertEquals("Lovelace", domain.lastName());
        assertEquals("ada@example.com", domain.emailId());
        assertEquals("5551234567", domain.phoneNumber());
        assertEquals("123-45-6789", domain.ssn());
    }

    @Test
    @DisplayName("mapToDomain: copies the compliance and pay fields across")
    void mapToDomain_copiesComplianceAndPay() {
        com.employeehub.domain.Employee domain = EmployeeMapper.mapToDomain(modelEmployee(), 1000L, 200L);

        assertEquals("Completed", domain.everifyStatus());
        assertEquals("Completed", domain.i9());
        assertEquals("submitted", domain.PAF());
        assertEquals("Aetna", domain.insurance());
        assertEquals(120000f, domain.annualPay());
        assertEquals("Intellan Technologies LLC", domain.companyName());
    }

    @Test
    @DisplayName("mapToDomain: carries the income and expense totals supplied by the caller")
    void mapToDomain_carriesIncomeAndExpense() {
        com.employeehub.domain.Employee domain = EmployeeMapper.mapToDomain(modelEmployee(), 1000L, 200L);

        assertEquals(1000L, domain.income());
        assertEquals(200L, domain.expense());
    }

    @Test
    @DisplayName("mapToDomain: accepts null income and expense (not every caller computes them)")
    void mapToDomain_nullIncomeAndExpense() {
        com.employeehub.domain.Employee domain = EmployeeMapper.mapToDomain(modelEmployee(), null, null);

        assertNull(domain.income());
        assertNull(domain.expense());
    }

    @Test
    @DisplayName("mapToDomain: leaves every address component null — the address is not mapped")
    void mapToDomain_doesNotMapAddress() {
        // Documented behaviour, relied on by callers that fetch the address
        // separately; asserted so a future address mapping is a deliberate,
        // visible change rather than a surprise.
        com.employeehub.domain.Employee domain = EmployeeMapper.mapToDomain(modelEmployee(), null, null);

        assertNull(domain.streetAddress());
        assertNull(domain.city());
        assertNull(domain.state());
        assertNull(domain.country());
        assertNull(domain.zipCode());
    }

    @Test
    @DisplayName("mapToDomain: an employee with no optional fields set maps without throwing")
    void mapToDomain_toleratesSparseEmployee() {
        Employee sparse = new Employee();
        sparse.setEmployeeId(7L);

        com.employeehub.domain.Employee domain = EmployeeMapper.mapToDomain(sparse, null, null);

        assertEquals(7L, domain.employeeId());
        assertNull(domain.firstName());
        assertNull(domain.status());
    }

    @Test
    @DisplayName("mapToModel then mapToDomain: round-trips the fields both mappers handle")
    void roundTrip_preservesSharedFields() {
        com.employeehub.domain.Employee original = domainEmployee(42L, "Code9 LLC");

        com.employeehub.domain.Employee roundTripped =
                EmployeeMapper.mapToDomain(EmployeeMapper.mapToModel(original), null, null);

        assertEquals(original.employeeId(), roundTripped.employeeId());
        assertEquals(original.firstName(), roundTripped.firstName());
        assertEquals(original.lastName(), roundTripped.lastName());
        assertEquals(original.emailId(), roundTripped.emailId());
        assertEquals(original.designation(), roundTripped.designation());
        assertEquals(original.employmentType(), roundTripped.employmentType());
        assertEquals(original.employeeType(), roundTripped.employeeType());
        assertEquals(original.vendorId(), roundTripped.vendorId());
        assertEquals(original.status(), roundTripped.status());
        assertEquals(original.location(), roundTripped.location());
        assertEquals(original.companyName(), roundTripped.companyName());
    }
}
