package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.EmployeeController;
import com.employeehub.model.Employee;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerSortTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private EmployeeController employeeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    // Simulate the fragmented DB order: IDs returned out of order (as Hibernate might return them)
    private List<Employee> unorderedEmployees;

    @BeforeEach
    void setUp() {
        CurrentUserContext.set("test@example.com", "ADMIN");
        mockMvc = MockMvcBuilders.standaloneSetup(employeeController).build();
        objectMapper = new ObjectMapper();

        Employee emp8  = makeEmployee(8L,  "Tharun Reddy",  "Gangasani",  "active",  "Intellan Technologies LLC");
        Employee emp1  = makeEmployee(1L,  "Appala Raju",   "Gurram",     "active",  "Intellan Technologies LLC");
        Employee emp13 = makeEmployee(13L, "Kirit Kumar",   "Gajari",     "active",  "Intellan Technologies LLC");
        Employee emp3  = makeEmployee(3L,  "Satish Kumar",  "Dhangye",    "active",  "Intellan Technologies LLC");
        Employee emp101= makeEmployee(101L,"Suresh R",      "Kandimalla", "active",  "Code9 LLC");
        Employee emp2  = makeEmployee(2L,  "Santosh",       "Kandimalla", "bench",   "Intellan Technologies LLC");
        Employee emp112= makeEmployee(112L,"Surya Mitra",   "Vedula",     "active",  "Code9 LLC");
        Employee emp5  = makeEmployee(5L,  "Shivananda",    "Khashidi",   "active",  "Intellan Technologies LLC");

        // Intentionally out of order — simulating Hibernate's return after OneToMany JOIN
        unorderedEmployees = Arrays.asList(emp8, emp1, emp13, emp3, emp101, emp2, emp112, emp5);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    // ── getAllEmployees ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllEmployees: returns employees sorted by employeeId ASC")
    void getAllEmployees_sortedByEmployeeId() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        MvcResult result = mockMvc.perform(get("/api/v1/employees/getAllEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andReturn();

        List<Long> ids = extractIds(result.getResponse().getContentAsString());
        assertSortedAsc(ids, "getAllEmployees");
    }

    @Test
    @DisplayName("getAllEmployees: first employee has lowest ID")
    void getAllEmployees_firstIdIsLowest() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        mockMvc.perform(get("/api/v1/employees/getAllEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(1));
    }

    @Test
    @DisplayName("getAllEmployees: last employee has highest ID")
    void getAllEmployees_lastIdIsHighest() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        mockMvc.perform(get("/api/v1/employees/getAllEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[7].employeeId").value(112));
    }

    @Test
    @DisplayName("getAllEmployees: Intellan IDs (1-13) appear before Code9 IDs (101+)")
    void getAllEmployees_intellanBeforeCode9() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        MvcResult result = mockMvc.perform(get("/api/v1/employees/getAllEmployees"))
                .andExpect(status().isOk())
                .andReturn();

        List<Long> ids = extractIds(result.getResponse().getContentAsString());
        int firstCode9Index  = ids.indexOf(101L);
        int lastIntellanIndex = Math.max(ids.indexOf(13L), Math.max(ids.indexOf(8L), ids.indexOf(5L)));
        assertTrue(lastIntellanIndex < firstCode9Index,
                "All Intellan employees should appear before Code9 employees");
    }

    @Test
    @DisplayName("getAllEmployees: returns empty list when no employees")
    void getAllEmployees_emptyList() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees/getAllEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── getEmployees (BasicEmployee) ─────────────────────────────────────────────

    @Test
    @DisplayName("getEmployees: returns BasicEmployees sorted by employeeId ASC")
    void getEmployees_sortedByEmployeeId() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        MvcResult result = mockMvc.perform(get("/api/v1/employees/getEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andReturn();

        List<Long> ids = extractEmployeeIds(result.getResponse().getContentAsString());
        assertSortedAsc(ids, "getEmployees");
    }

    @Test
    @DisplayName("getEmployees: first BasicEmployee has lowest ID")
    void getEmployees_firstIdIsLowest() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        mockMvc.perform(get("/api/v1/employees/getEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(1));
    }

    @Test
    @DisplayName("getEmployees: last BasicEmployee has highest ID")
    void getEmployees_lastIdIsHighest() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        mockMvc.perform(get("/api/v1/employees/getEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[7].employeeId").value(112));
    }

    @Test
    @DisplayName("getEmployees: name is concatenated as 'firstName lastName'")
    void getEmployees_nameIsConcatenated() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        mockMvc.perform(get("/api/v1/employees/getEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Appala Raju Gurram"));
    }

    @Test
    @DisplayName("getEmployees: status is preserved correctly")
    void getEmployees_statusIsPreserved() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(unorderedEmployees);

        mockMvc.perform(get("/api/v1/employees/getEmployees"))
                .andExpect(status().isOk())
                // emp ID=2 Santosh is on bench — find it in sorted position index 1
                .andExpect(jsonPath("$[1].status").value("bench"));
    }

    @Test
    @DisplayName("getEmployees: returns empty list when no employees")
    void getEmployees_emptyList() throws Exception {
        when(employeeRepository.findAllSorted()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees/getEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private Employee makeEmployee(long id, String firstName, String lastName, String status, String companyName) {
        Employee emp = new Employee();
        emp.setEmployeeId(id);
        emp.setFirstName(firstName);
        emp.setLastName(lastName);
        emp.setStatus(status);
        emp.setCompanyName(companyName);
        return emp;
    }

    /** Extract employeeId values from a JSON array of full Employee objects */
    private List<Long> extractIds(String json) throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
        List<Long> ids = new java.util.ArrayList<>();
        root.forEach(node -> ids.add(node.get("employeeId").asLong()));
        return ids;
    }

    /** Extract employeeId values from a JSON array of BasicEmployee objects */
    private List<Long> extractEmployeeIds(String json) throws Exception {
        return extractIds(json); // same field name
    }

    private void assertSortedAsc(List<Long> ids, String endpoint) {
        for (int i = 0; i < ids.size() - 1; i++) {
            assertTrue(ids.get(i) < ids.get(i + 1),
                    endpoint + ": expected ID " + ids.get(i) + " < " + ids.get(i + 1)
                            + " at index " + i + " but order was wrong. Full list: " + ids);
        }
    }
}
