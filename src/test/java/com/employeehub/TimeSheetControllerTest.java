package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.TimeSheetController;
import com.employeehub.model.TimeSheet;
import com.employeehub.repository.TimeSheetRepository;
import com.employeehub.service.TimeSheetService;
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

import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role gate for every write/action endpoint on TimeSheetController. Save/
 * Submit/Reject/Delete are ADMIN/ACCOUNTING/IMMIGRATION; Approve/Reopen are
 * ADMIN-only — a deliberately narrower restriction than the others, added
 * so final sign-off and reopening an already-approved month can't be done
 * by ACCOUNTING/IMMIGRATION. This file exists specifically to catch that
 * restriction being accidentally loosened (or the others accidentally
 * narrowed) on a future change, since no test caught it when it was first
 * introduced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeSheetControllerTest {

    @Mock
    private TimeSheetService timeSheetService;

    @Mock
    private TimeSheetRepository timeSheetRepository;

    @InjectMocks
    private TimeSheetController timeSheetController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(timeSheetController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private void setRole(String role) {
        CurrentUserContext.set("user@example.com", role);
    }

    // ---- saveBulk: ADMIN/ACCOUNTING/IMMIGRATION ----

    @Test
    @DisplayName("saveBulk: allowed for ACCOUNTING")
    void saveBulk_allowedForAccounting() throws Exception {
        setRole("ACCOUNTING");
        when(timeSheetService.saveBulk(any())).thenReturn(
                new TimeSheetService.SaveResult(List.of(), List.of(), List.of()));

        mockMvc.perform(post("/api/v1/timesheets/saveBulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("saveBulk: forbidden for HR")
    void saveBulk_forbiddenForHr() throws Exception {
        setRole("HR");
        mockMvc.perform(post("/api/v1/timesheets/saveBulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isForbidden());
        verify(timeSheetService, never()).saveBulk(any());
    }

    // ---- submit: ADMIN/ACCOUNTING/IMMIGRATION ----

    @Test
    @DisplayName("submit: allowed for IMMIGRATION")
    void submit_allowedForImmigration() throws Exception {
        setRole("IMMIGRATION");
        when(timeSheetService.submitMonth(1L, YearMonth.of(2026, 1))).thenReturn(5);

        mockMvc.perform(post("/api/v1/timesheets/submit")
                        .param("assignmentId", "1")
                        .param("yearMonth", "2026-01"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("submit: forbidden for HR")
    void submit_forbiddenForHr() throws Exception {
        setRole("HR");
        mockMvc.perform(post("/api/v1/timesheets/submit")
                        .param("assignmentId", "1")
                        .param("yearMonth", "2026-01"))
                .andExpect(status().isForbidden());
        verify(timeSheetService, never()).submitMonth(anyLong(), any());
    }

    // ---- approve: ADMIN-only (narrower than submit/save/reject/delete) ----

    @Test
    @DisplayName("approve: allowed for ADMIN")
    void approve_allowedForAdmin() throws Exception {
        setRole("ADMIN");
        when(timeSheetService.approveMonth(1L, YearMonth.of(2026, 1))).thenReturn(5);

        mockMvc.perform(post("/api/v1/timesheets/approve")
                        .param("assignmentId", "1")
                        .param("yearMonth", "2026-01"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("approve: forbidden for ACCOUNTING")
    void approve_forbiddenForAccounting() throws Exception {
        setRole("ACCOUNTING");
        mockMvc.perform(post("/api/v1/timesheets/approve")
                        .param("assignmentId", "1")
                        .param("yearMonth", "2026-01"))
                .andExpect(status().isForbidden());
        verify(timeSheetService, never()).approveMonth(anyLong(), any());
    }

    @Test
    @DisplayName("approve: forbidden for IMMIGRATION")
    void approve_forbiddenForImmigration() throws Exception {
        setRole("IMMIGRATION");
        mockMvc.perform(post("/api/v1/timesheets/approve")
                        .param("assignmentId", "1")
                        .param("yearMonth", "2026-01"))
                .andExpect(status().isForbidden());
    }

    // ---- reopen: ADMIN-only ----

    @Test
    @DisplayName("reopen: allowed for ADMIN")
    void reopen_allowedForAdmin() throws Exception {
        setRole("ADMIN");
        when(timeSheetService.reopenMonth(1L, YearMonth.of(2026, 1))).thenReturn(3);

        mockMvc.perform(post("/api/v1/timesheets/reopen")
                        .param("assignmentId", "1")
                        .param("yearMonth", "2026-01"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reopen: forbidden for ACCOUNTING")
    void reopen_forbiddenForAccounting() throws Exception {
        setRole("ACCOUNTING");
        mockMvc.perform(post("/api/v1/timesheets/reopen")
                        .param("assignmentId", "1")
                        .param("yearMonth", "2026-01"))
                .andExpect(status().isForbidden());
        verify(timeSheetService, never()).reopenMonth(anyLong(), any());
    }

    // ---- submitRange / approveRange / rejectRange / reopenRange: same
    // split as the month-scoped versions above (approve/reopen ADMIN-only,
    // the rest ADMIN/ACCOUNTING/IMMIGRATION) ----

    @Test
    @DisplayName("submitRange: allowed for ACCOUNTING")
    void submitRange_allowedForAccounting() throws Exception {
        setRole("ACCOUNTING");
        when(timeSheetService.submitRange(eq(1L), any(), any())).thenReturn(5);

        mockMvc.perform(post("/api/v1/timesheets/submitRange")
                        .param("assignmentId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("approveRange: forbidden for ACCOUNTING")
    void approveRange_forbiddenForAccounting() throws Exception {
        setRole("ACCOUNTING");
        mockMvc.perform(post("/api/v1/timesheets/approveRange")
                        .param("assignmentId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isForbidden());
        verify(timeSheetService, never()).approveRange(anyLong(), any(), any());
    }

    @Test
    @DisplayName("approveRange: allowed for ADMIN")
    void approveRange_allowedForAdmin() throws Exception {
        setRole("ADMIN");
        when(timeSheetService.approveRange(eq(1L), any(), any())).thenReturn(5);

        mockMvc.perform(post("/api/v1/timesheets/approveRange")
                        .param("assignmentId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rejectRange: allowed for IMMIGRATION")
    void rejectRange_allowedForImmigration() throws Exception {
        setRole("IMMIGRATION");
        when(timeSheetService.rejectRange(eq(1L), any(), any())).thenReturn(2);

        mockMvc.perform(post("/api/v1/timesheets/rejectRange")
                        .param("assignmentId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rejectRange: forbidden for HR")
    void rejectRange_forbiddenForHr() throws Exception {
        setRole("HR");
        mockMvc.perform(post("/api/v1/timesheets/rejectRange")
                        .param("assignmentId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("reopenRange: forbidden for IMMIGRATION")
    void reopenRange_forbiddenForImmigration() throws Exception {
        setRole("IMMIGRATION");
        mockMvc.perform(post("/api/v1/timesheets/reopenRange")
                        .param("assignmentId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isForbidden());
        verify(timeSheetService, never()).reopenRange(anyLong(), any(), any());
    }

    @Test
    @DisplayName("reopenRange: allowed for ADMIN")
    void reopenRange_allowedForAdmin() throws Exception {
        setRole("ADMIN");
        when(timeSheetService.reopenRange(eq(1L), any(), any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/timesheets/reopenRange")
                        .param("assignmentId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-07"))
                .andExpect(status().isOk());
    }

    // ---- delete: ADMIN/ACCOUNTING/IMMIGRATION ----

    @Test
    @DisplayName("delete: allowed for ACCOUNTING")
    void delete_allowedForAccounting() throws Exception {
        setRole("ACCOUNTING");
        mockMvc.perform(delete("/api/v1/timesheets/5"))
                .andExpect(status().isOk());
        verify(timeSheetRepository).deleteById(5L);
    }

    @Test
    @DisplayName("delete: forbidden for HR")
    void delete_forbiddenForHr() throws Exception {
        setRole("HR");
        mockMvc.perform(delete("/api/v1/timesheets/5"))
                .andExpect(status().isForbidden());
        verify(timeSheetRepository, never()).deleteById(anyLong());
    }

    // ---- read endpoints: open to any authenticated role (no requireRole
    // call at all) — backs the Timesheets grid and reports, which every
    // non-ADMIN role can view ----

    @Test
    @DisplayName("summary: readable by HR, which has no write access at all")
    void summary_readableByHr() throws Exception {
        setRole("HR");
        when(timeSheetService.getTimesheetSummaries()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/timesheets/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("byInvoiceFrequency: readable by HR")
    void byInvoiceFrequency_readableByHr() throws Exception {
        setRole("HR");
        when(timeSheetService.getTimesheetsByInvoiceFrequency()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/timesheets/byInvoiceFrequency"))
                .andExpect(status().isOk());
    }
}
