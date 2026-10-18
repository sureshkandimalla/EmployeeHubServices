package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.AdjustmentController;
import com.employeehub.model.Adjustment;
import com.employeehub.repository.AdjustmentRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adjustments move money between employees, so every endpoint on
 * AdjustmentController is restricted to ADMIN/ACCOUNTING. AdjustmentControllerTest
 * covers the amount-sign logic; this file covers the role gate on all three
 * endpoints plus the remaining success/failure paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdjustmentControllerRoleTest {

    @Mock
    private AdjustmentRepository adjustmentRepository;

    @InjectMocks
    private AdjustmentController adjustmentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adjustmentController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        CurrentUserContext.set("acct@example.com", "ACCOUNTING");
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /** Row shape matches domain.Adjustment(Object[]). */
    private Object[] row(long adjustmentId, double amount, long fromId, long toId) {
        Date today = Date.valueOf("2026-01-01");
        return new Object[]{BigInteger.valueOf(adjustmentId), today, today, "Bonus", amount, "note",
                BigInteger.valueOf(fromId), BigInteger.valueOf(toId), "From Name", "To Name"};
    }

    // ── GET /getAdjustments ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /getAdjustments: returns 200 with every adjustment for an ACCOUNTING user")
    void getAdjustments_returnsListForAccounting() throws Exception {
        when(adjustmentRepository.findAllAdjustmentsWithNames())
                .thenReturn(Arrays.asList(row(1L, 100.0, 2L, 1L), row(2L, 200.0, 3L, 1L)));

        mockMvc.perform(get("/api/v1/adjustment/getAdjustments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /getAdjustments: an ADMIN user may also read adjustments")
    void getAdjustments_allowedForAdmin() throws Exception {
        CurrentUserContext.set("admin@example.com", "ADMIN");
        when(adjustmentRepository.findAllAdjustmentsWithNames()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/adjustment/getAdjustments"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /getAdjustments: returns 403 for an HR user")
    void getAdjustments_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(get("/api/v1/adjustment/getAdjustments"))
                .andExpect(status().isForbidden());

        verify(adjustmentRepository, never()).findAllAdjustmentsWithNames();
    }

    @Test
    @DisplayName("GET /getAdjustments: returns 403 for an IMMIGRATION user")
    void getAdjustments_forbiddenForImmigration() throws Exception {
        CurrentUserContext.set("immi@example.com", "IMMIGRATION");

        mockMvc.perform(get("/api/v1/adjustment/getAdjustments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /getAdjustments: returns 403 when the caller has no role assigned")
    void getAdjustments_forbiddenWithoutRole() throws Exception {
        CurrentUserContext.set("nobody@example.com", null);

        mockMvc.perform(get("/api/v1/adjustment/getAdjustments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /getAdjustments: returns 200 with an empty array when there are none")
    void getAdjustments_emptyList() throws Exception {
        when(adjustmentRepository.findAllAdjustmentsWithNames()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/adjustment/getAdjustments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /findAdjustmentsByEmployeeId ────────────────────────────────────

    @Test
    @DisplayName("GET /findAdjustmentsByEmployeeId: keeps the amount positive when the employee is the payer")
    void findAdjustmentsByEmployeeId_keepsPositiveForPayer() throws Exception {
        // fromId == the queried employee, so the amount is money they owe out
        // and keeps the sign it was stored with.
        when(adjustmentRepository.findAdjustmentsByEmployeeId(1L))
                .thenReturn(Arrays.<Object[]>asList(row(1L, 100.0, 1L, 2L)));

        mockMvc.perform(get("/api/v1/adjustment/findAdjustmentsByEmployeeId").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount", is(100.0)));
    }

    @Test
    @DisplayName("GET /findAdjustmentsByEmployeeId: an already-negative amount owed to the employee is not double-negated")
    void findAdjustmentsByEmployeeId_doesNotDoubleNegate() throws Exception {
        when(adjustmentRepository.findAdjustmentsByEmployeeId(1L))
                .thenReturn(Arrays.<Object[]>asList(row(1L, -100.0, 2L, 1L)));

        mockMvc.perform(get("/api/v1/adjustment/findAdjustmentsByEmployeeId").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount", is(-100.0)));
    }

    @Test
    @DisplayName("GET /findAdjustmentsByEmployeeId: returns 200 with an empty array when the employee has none")
    void findAdjustmentsByEmployeeId_emptyList() throws Exception {
        when(adjustmentRepository.findAdjustmentsByEmployeeId(42L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/adjustment/findAdjustmentsByEmployeeId").param("id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /findAdjustmentsByEmployeeId: returns 400 when the id parameter is missing")
    void findAdjustmentsByEmployeeId_badRequestWithoutId() throws Exception {
        mockMvc.perform(get("/api/v1/adjustment/findAdjustmentsByEmployeeId"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /findAdjustmentsByEmployeeId: returns 403 for an HR user")
    void findAdjustmentsByEmployeeId_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(get("/api/v1/adjustment/findAdjustmentsByEmployeeId").param("id", "1"))
                .andExpect(status().isForbidden());

        verify(adjustmentRepository, never()).findAdjustmentsByEmployeeId(anyLong());
    }

    // ── POST /addAdjustment ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /addAdjustment: returns 200 with the saved adjustment")
    void addAdjustment_returns200() throws Exception {
        Adjustment saved = new Adjustment();
        saved.setAdjustmentId(9L);
        saved.setAmount(250.0);
        saved.setAdjustmentType("Bonus");
        when(adjustmentRepository.save(any(Adjustment.class))).thenReturn(saved);

        Adjustment request = new Adjustment();
        request.setAmount(250.0);
        request.setAdjustmentType("Bonus");

        mockMvc.perform(post("/api/v1/adjustment/addAdjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustmentId").value(9))
                .andExpect(jsonPath("$.amount").value(250.0));
    }

    @Test
    @DisplayName("POST /addAdjustment: returns 403 for an HR user and saves nothing")
    void addAdjustment_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(post("/api/v1/adjustment/addAdjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Adjustment())))
                .andExpect(status().isForbidden());

        verify(adjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST /addAdjustment: returns 400 for a malformed JSON body")
    void addAdjustment_badRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/adjustment/addAdjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /addAdjustment: a repository failure surfaces as DataNotSavedException")
    void addAdjustment_wrapsRepositoryFailure() {
        when(adjustmentRepository.save(any(Adjustment.class))).thenThrow(new RuntimeException("db down"));

        // No global @ExceptionHandler is registered, so DataNotSavedException
        // propagates wrapped in NestedServletException (same convention as
        // EmployeeControllerSaveTest).
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.util.NestedServletException.class,
                () -> mockMvc.perform(post("/api/v1/adjustment/addAdjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Adjustment()))));
    }
}
