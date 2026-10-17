package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.EmployeeController;
import com.employeehub.domain.Employee;
import com.employeehub.exception.EmployeeServiceException;
import com.employeehub.model.Address;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerSaveTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private EmployeeController employeeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        CurrentUserContext.set("test@example.com", "HR");
        mockMvc = MockMvcBuilders.standaloneSetup(employeeController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    // ── helper: build a saved Employee model entity ──────────────────────────────

    private com.employeehub.model.Employee makeSavedEmployee(long id, String i9, String paf, String companyName) {
        com.employeehub.model.Employee saved = new com.employeehub.model.Employee();
        saved.setEmployeeId(id);
        saved.setFirstName("John");
        saved.setLastName("Doe");
        saved.setEmailId("john.doe@example.com");
        saved.setPhone("1234567890");
        saved.setDob("1990-01-01");
        saved.setGender("MALE");
        saved.setSsn("123-45-6789");
        saved.setVisa("H1B");
        saved.setTaxTerm("W2");
        saved.setStatus("active");
        saved.setDesignation("Software Engineer");
        saved.setLocation("Dallas");
        saved.setEmploymentType("Full-time");
        saved.setStartDate(LocalDate.of(2024, 1, 15));
        saved.setI9(i9);
        saved.setPAF(paf);
        saved.setEverifyStatus("Authorized");
        saved.setCompanyName(companyName);
        saved.setLastUpdated(LocalDate.now());

        Address addr = new Address();
        addr.setAddress("123 Main St");
        addr.setCity("Dallas");
        addr.setState("TX");
        addr.setZipCode("75001");
        addr.setCountry("USA");
        saved.setAddress(new ArrayList<>());
        saved.getAddress().add(addr);

        return saved;
    }

    // ── helper: build the domain request JSON ────────────────────────────────────

    private Employee makeDomainEmployee(String i9, String paf, String companyName) {
        return new Employee(
                null,                   // employeeId — null for new employee
                "Software Engineer",    // designation
                "1990-01-01",           // dob
                "123-45-6789",          // ssn
                "1234567890",           // phoneNumber
                "john.doe@example.com", // emailId
                "Full-time",            // employmentType
                null,                   // employeeType
                null,                   // vendorId
                "John",                 // firstName
                "Doe",                  // lastName
                "male",                 // gender
                LocalDate.of(2024, 1, 15), // startDate
                null,                   // endDate
                null,                   // referredBy
                "W2",                   // taxTerms
                "H1B",                  // visa
                "123 Main St",          // streetAddress
                null,                   // address_line_2
                "Dallas",               // city
                "TX",                   // state
                "USA",                  // country
                "75001",                // zipCode
                "active",               // status
                "Dallas",               // location
                null,                   // income
                null,                   // expense
                "Authorized",           // everifyStatus
                i9,                     // i9
                paf,                    // PAF
                null,                   // insurance
                0f,                     // annualPay
                companyName             // companyName
        );
    }

    // ── Tests ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveOnBoardDetails: returns 200 OK with saved employee in response body")
    void saveOnBoardDetails_returns200WithEmployee() throws Exception {
        com.employeehub.model.Employee savedEmployee = makeSavedEmployee(1L, "signed", "submitted", "Intellan Technologies LLC");
        when(employeeService.saveEmployeeDetails(any())).thenReturn(Optional.of(savedEmployee));

        Employee request = makeDomainEmployee("signed", "submitted", "Intellan Technologies LLC");

        mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @DisplayName("saveOnBoardDetails: response body contains i9 field value")
    void saveOnBoardDetails_responseContainsI9() throws Exception {
        com.employeehub.model.Employee savedEmployee = makeSavedEmployee(1L, "signed", "submitted", "Intellan Technologies LLC");
        when(employeeService.saveEmployeeDetails(any())).thenReturn(Optional.of(savedEmployee));

        Employee request = makeDomainEmployee("signed", "submitted", "Intellan Technologies LLC");

        mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.i9").value("signed"));
    }

    @Test
    @DisplayName("saveOnBoardDetails: response body contains paf field value")
    void saveOnBoardDetails_responseContainsPAF() throws Exception {
        com.employeehub.model.Employee savedEmployee = makeSavedEmployee(1L, "signed", "submitted", "Intellan Technologies LLC");
        when(employeeService.saveEmployeeDetails(any())).thenReturn(Optional.of(savedEmployee));

        Employee request = makeDomainEmployee("signed", "submitted", "Intellan Technologies LLC");

        mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paf").value("submitted"));
    }

    @Test
    @DisplayName("saveOnBoardDetails: response contains companyName for Intellan employee")
    void saveOnBoardDetails_responseContainsCompanyName() throws Exception {
        com.employeehub.model.Employee savedEmployee = makeSavedEmployee(5L, "signed", "submitted", "Intellan Technologies LLC");
        when(employeeService.saveEmployeeDetails(any())).thenReturn(Optional.of(savedEmployee));

        Employee request = makeDomainEmployee("signed", "submitted", "Intellan Technologies LLC");

        mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Intellan Technologies LLC"));
    }

    @Test
    @DisplayName("saveOnBoardDetails: response contains companyName for Code9 employee")
    void saveOnBoardDetails_code9EmployeeReturned() throws Exception {
        com.employeehub.model.Employee savedEmployee = makeSavedEmployee(101L, "signed", "submitted", "Code9 LLC");
        when(employeeService.saveEmployeeDetails(any())).thenReturn(Optional.of(savedEmployee));

        Employee request = makeDomainEmployee("signed", "submitted", "Code9 LLC");

        mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(101))
                .andExpect(jsonPath("$.companyName").value("Code9 LLC"));
    }

    @Test
    @DisplayName("saveOnBoardDetails: response contains everifyStatus")
    void saveOnBoardDetails_responseContainsEverifyStatus() throws Exception {
        com.employeehub.model.Employee savedEmployee = makeSavedEmployee(1L, "signed", "submitted", "Intellan Technologies LLC");
        when(employeeService.saveEmployeeDetails(any())).thenReturn(Optional.of(savedEmployee));

        Employee request = makeDomainEmployee("signed", "submitted", "Intellan Technologies LLC");

        mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.everifyStatus").value("Authorized"));
    }

    @Test
    @DisplayName("saveOnBoardDetails: service exception propagates and is thrown")
    void saveOnBoardDetails_serviceExceptionPropagates() throws Exception {
        when(employeeService.saveEmployeeDetails(any()))
                .thenThrow(new EmployeeServiceException("Employee data not saved", null));

        Employee request = makeDomainEmployee("signed", "submitted", "Intellan Technologies LLC");

        // No global @ExceptionHandler is registered — EmployeeServiceException propagates
        // wrapped in NestedServletException from MockMvc's dispatcher servlet.
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.util.NestedServletException.class,
                () -> mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
        );
    }

    @Test
    @DisplayName("saveOnBoardDetails: response body is not empty")
    void saveOnBoardDetails_responseBodyNotNull() throws Exception {
        com.employeehub.model.Employee savedEmployee = makeSavedEmployee(3L, "pending", "pending", "Intellan Technologies LLC");
        when(employeeService.saveEmployeeDetails(any())).thenReturn(Optional.of(savedEmployee));

        Employee request = makeDomainEmployee("pending", "pending", "Intellan Technologies LLC");

        mockMvc.perform(post("/api/v1/employees/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.employeeId").exists())
                .andExpect(jsonPath("$.firstName").exists());
    }
}
