package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.VisaController;
import com.employeehub.model.Employee;
import com.employeehub.model.LCA;
import com.employeehub.model.PotentialEmployee;
import com.employeehub.model.Visa;
import com.employeehub.repository.PemployeeRepository;
import com.employeehub.service.VisaService;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VisaControllerTest {

    @Mock
    private VisaService visaService;

    @Mock
    private PemployeeRepository pemployeeRepository;

    @InjectMocks
    private VisaController visaController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private Visa visa1;
    private Visa visa2;
    private Employee employee;

    @BeforeEach
    void setUp() {
        CurrentUserContext.set("test@example.com", "ADMIN");
        mockMvc = MockMvcBuilders.standaloneSetup(visaController)
                .setValidator(new org.springframework.validation.Validator() {
                    @Override
                    public boolean supports(Class<?> clazz) {
                        return true;
                    }

                    @Override
                    public void validate(Object target, org.springframework.validation.Errors errors) {
                        // no-op validator for standalone MockMvc tests
                    }
                })
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        employee = new Employee();
        employee.setEmployeeId(1L);
        employee.setFirstName("John");
        employee.setLastName("Doe");

        visa1 = makeVisa(101L, "H1B", "REC-001", employee,
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1));
        visa2 = makeVisa(102L, "H1B", "REC-002", employee,
                LocalDate.of(2024, 6, 1), LocalDate.of(2027, 6, 1));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    // ── GET /getAllVisas ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /getAllVisas: returns 200 with list of all visas")
    void getAllVisas_returns200WithList() throws Exception {
        when(visaService.getAllVisas()).thenReturn(Arrays.asList(visa1, visa2));

        mockMvc.perform(get("/api/v1/visa/getAllVisas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].visaId").value(101))
                .andExpect(jsonPath("$[1].visaId").value(102));
    }

    @Test
    @DisplayName("GET /getAllVisas: returns empty list when no visas")
    void getAllVisas_returnsEmptyList() throws Exception {
        when(visaService.getAllVisas()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/visa/getAllVisas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /visa/{id} ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /visa/{id}: returns 200 with visa when found")
    void getVisaById_found() throws Exception {
        when(visaService.getVisaById(101L)).thenReturn(Optional.of(visa1));

        mockMvc.perform(get("/api/v1/visa/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visaId").value(101))
                .andExpect(jsonPath("$.receiptNumber").value("REC-001"))
                .andExpect(jsonPath("$.visaCategory").value("H1B"));
    }

    @Test
    @DisplayName("GET /visa/{id}: returns 404 when visa not found")
    void getVisaById_notFound() throws Exception {
        when(visaService.getVisaById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/visa/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /employee/{employeeId} ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /employee/{id}: returns all visas for employee")
    void getVisasByEmployee_returnsList() throws Exception {
        when(visaService.getVisasByEmployeeId(1L)).thenReturn(Arrays.asList(visa1, visa2));

        mockMvc.perform(get("/api/v1/visa/employee/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].receiptNumber").value("REC-001"))
                .andExpect(jsonPath("$[1].receiptNumber").value("REC-002"));
    }

    @Test
    @DisplayName("GET /employee/{id}: returns empty list when employee has no visas")
    void getVisasByEmployee_emptyList() throws Exception {
        when(visaService.getVisasByEmployeeId(99L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/visa/employee/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /employee/{id}/latest ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /employee/{id}/latest: returns most recent visa")
    void getLatestVisaByEmployee_found() throws Exception {
        when(visaService.getLatestVisaByEmployeeId(1L)).thenReturn(Optional.of(visa2));

        mockMvc.perform(get("/api/v1/visa/employee/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visaId").value(102))
                .andExpect(jsonPath("$.receiptNumber").value("REC-002"));
    }

    @Test
    @DisplayName("GET /employee/{id}/latest: returns 404 when no visa found")
    void getLatestVisaByEmployee_notFound() throws Exception {
        when(visaService.getLatestVisaByEmployeeId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/visa/employee/99/latest"))
                .andExpect(status().isNotFound());
    }

    // ── POST /createVisa ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /createVisa: returns 200 with saved visa")
    void createVisa_returns200() throws Exception {
        when(visaService.saveVisa(any(Visa.class))).thenReturn(visa1);

        mockMvc.perform(post("/api/v1/visa/createVisa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(visa1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visaId").value(101))
                .andExpect(jsonPath("$.receiptNumber").value("REC-001"));
    }

    @Test
    @DisplayName("POST /createVisa: visa with LCA returns saved visa with lca")
    void createVisa_withLca() throws Exception {
        LCA lca = new LCA();
        lca.setLcaId(201L);
        lca.setLcaNumber("LCA-2024-001");
        visa1.setLca(lca);
        when(visaService.saveVisa(any(Visa.class))).thenReturn(visa1);

        mockMvc.perform(post("/api/v1/visa/createVisa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(visa1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visaId").value(101));
    }

    // ── PUT /visa/{id} ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /visa/{id}: returns 200 with updated visa")
    void updateVisa_returns200() throws Exception {
        Visa updated = makeVisa(101L, "L1A", "REC-UPDATED", employee,
                LocalDate.of(2025, 1, 1), LocalDate.of(2028, 1, 1));
        updated.setJobTitle("Tech Lead");
        updated.setClient("Acme Corp");

        when(visaService.updateVisa(eq(101L), any(Visa.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/visa/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visaCategory").value("L1A"))
                .andExpect(jsonPath("$.receiptNumber").value("REC-UPDATED"))
                .andExpect(jsonPath("$.jobTitle").value("Tech Lead"));
    }

    // ── DELETE /visa/{id} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /visa/{id}: returns 200 with deleted=true")
    void deleteVisa_returns200() throws Exception {
        doNothing().when(visaService).deleteVisa(101L);

        mockMvc.perform(delete("/api/v1/visa/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(visaService).deleteVisa(101L);
    }

    // ── POST /potentialEmployees ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /potentialEmployees: returns 200 with saved potential employee")
    void createPotentialEmployee_returns200() throws Exception {
        PotentialEmployee pe = new PotentialEmployee();
        pe.setFirstName("Jane");
        pe.setLastName("Smith");
        when(pemployeeRepository.save(any(PotentialEmployee.class))).thenReturn(pe);

        mockMvc.perform(post("/api/v1/visa/potentialEmployees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    // ── GET /getAllPotentialEmployees ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /getAllPotentialEmployees: returns 200 with list")
    void getAllPotentialEmployees_returns200() throws Exception {
        PotentialEmployee pe1 = new PotentialEmployee();
        pe1.setFirstName("Jane");
        PotentialEmployee pe2 = new PotentialEmployee();
        pe2.setFirstName("Bob");
        when(pemployeeRepository.findAll()).thenReturn(Arrays.asList(pe1, pe2));

        mockMvc.perform(get("/api/v1/visa/getAllPotentialEmployees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── POST /savePotentialEmployees ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /savePotentialEmployees: returns 200 with saved list")
    void savePotentialEmployees_returns200() throws Exception {
        PotentialEmployee pe1 = new PotentialEmployee();
        pe1.setFirstName("Jane");
        PotentialEmployee pe2 = new PotentialEmployee();
        pe2.setFirstName("Bob");
        List<PotentialEmployee> list = Arrays.asList(pe1, pe2);
        when(pemployeeRepository.saveAll(any())).thenReturn(list);

        mockMvc.perform(post("/api/v1/visa/savePotentialEmployees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(list)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── helper ───────────────────────────────────────────────────────────────────

    private Visa makeVisa(long id, String category, String receiptNumber,
                          Employee emp, LocalDate start, LocalDate end) {
        Visa v = new Visa();
        v.setVisaId(id);
        v.setVisaCategory(category);
        v.setReceiptNumber(receiptNumber);
        v.setEmployee(emp);
        v.setStartDate(start);
        v.setEndDate(end);
        v.setStatus("active");
        return v;
    }
}
