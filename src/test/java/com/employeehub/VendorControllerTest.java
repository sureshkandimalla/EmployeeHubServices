package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.VendorController;
import com.employeehub.model.Vendor;
import com.employeehub.repository.VendorRepository;
import com.employeehub.service.VendorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Vendor endpoints: role gating on every write, 404 on missing records, and
 * the partial-update contract on PUT.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorControllerTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private VendorService vendorService;

    @InjectMocks
    private VendorController vendorController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(vendorController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        CurrentUserContext.set("acct@example.com", "ACCOUNTING");
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private Vendor vendor(long id, String companyName, String status) {
        Vendor v = new Vendor();
        v.setVendorId(id);
        v.setVendorCompanyName(companyName);
        v.setVendorName("Sam Supplier");
        v.setVendorEmail("sam@supplier.test");
        v.setVendorStatus(status);
        v.setVendorStartDate(LocalDate.of(2026, 2, 1));
        v.setPaymentTerms("Net 45");
        v.setVendorType("Staffing");
        return v;
    }

    private com.employeehub.domain.Vendor onboardRequest() {
        return new com.employeehub.domain.Vendor(
                null, "Sam Supplier", "98-7654321", "5559876543", "sam@supplier.test",
                LocalDate.of(2026, 2, 1), null, "Supplier Inc", "https://supplier.test",
                "500 Oak Ave", null, "Austin", "TX", "73301", "USA",
                "Staffing", "Net 45", "Pay on receipt", "Pending");
    }

    // ── GET /getAllVendors ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /getAllVendors: returns 200 with every vendor")
    void getAllVendors_returnsList() throws Exception {
        when(vendorRepository.findAll()).thenReturn(List.of(
                vendor(1L, "Supplier Inc", "Active"), vendor(2L, "Other Inc", "Pending")));

        mockMvc.perform(get("/api/v1/vendors/getAllVendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].vendorCompanyName").value("Supplier Inc"));
    }

    @Test
    @DisplayName("GET /getAllVendors: returns 200 with an empty array when there are no vendors")
    void getAllVendors_emptyList() throws Exception {
        when(vendorRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vendors/getAllVendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── POST /saveOnBoardDetails ────────────────────────────────────────────

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 200 with the saved vendor for an ACCOUNTING user")
    void createVendor_savesForAccounting() throws Exception {
        when(vendorService.saveVendor(any())).thenReturn(Optional.of(vendor(1L, "Supplier Inc", "Pending")));

        mockMvc.perform(post("/api/v1/vendors/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(1))
                .andExpect(jsonPath("$.vendorStatus").value("Pending"));
    }

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 403 when an HR user tries to create a vendor")
    void createVendor_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(post("/api/v1/vendors/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardRequest())))
                .andExpect(status().isForbidden());

        verify(vendorService, never()).saveVendor(any());
    }

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 403 when the caller has no role assigned")
    void createVendor_forbiddenWithoutRole() throws Exception {
        CurrentUserContext.set("nobody@example.com", null);

        mockMvc.perform(post("/api/v1/vendors/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 400 for a malformed JSON body")
    void createVendor_badRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/vendors/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nope"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /vendors/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /vendors/{id}: returns 200 with the vendor")
    void getVendorById_found() throws Exception {
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(vendor(1L, "Supplier Inc", "Active")));

        mockMvc.perform(get("/api/v1/vendors/vendors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(1));
    }

    @Test
    @DisplayName("GET /vendors/{id}: returns 404 for an unknown vendor")
    void getVendorById_notFound() throws Exception {
        when(vendorRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vendors/vendors/999"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /vendors/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /vendors/{id}: returns 200 and applies the supplied fields")
    void updateVendor_appliesSuppliedFields() throws Exception {
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(vendor(1L, "Supplier Inc", "Pending")));
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

        Vendor body = new Vendor();
        body.setVendorStatus("Active");

        mockMvc.perform(put("/api/v1/vendors/vendors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorStatus").value("Active"));
    }

    @Test
    @DisplayName("PUT /vendors/{id}: fields omitted from the body keep their stored values")
    void updateVendor_isPartialUpdate() throws Exception {
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(vendor(1L, "Supplier Inc", "Pending")));
        ArgumentCaptor<Vendor> captor = ArgumentCaptor.forClass(Vendor.class);
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

        Vendor body = new Vendor();
        body.setVendorStatus("Active");

        mockMvc.perform(put("/api/v1/vendors/vendors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(vendorRepository).save(captor.capture());
        assertEquals("Net 45", captor.getValue().getPaymentTerms());
        assertEquals("Staffing", captor.getValue().getVendorType());
        assertEquals("sam@supplier.test", captor.getValue().getVendorEmail());
    }

    @Test
    @DisplayName("PUT /vendors/{id}: returns 404 for an unknown vendor and saves nothing")
    void updateVendor_notFound() throws Exception {
        when(vendorRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/vendors/vendors/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Vendor())))
                .andExpect(status().isNotFound());

        verify(vendorRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT /vendors/{id}: returns 403 when an HR user tries to edit a vendor")
    void updateVendor_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(put("/api/v1/vendors/vendors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Vendor())))
                .andExpect(status().isForbidden());

        verify(vendorRepository, never()).save(any());
    }

    // ── DELETE /vendors/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /vendors/{id}: returns 200 with deleted=true and removes the vendor")
    void deleteVendor_deletes() throws Exception {
        Vendor existing = vendor(1L, "Supplier Inc", "Active");
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/v1/vendors/vendors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(vendorRepository).delete(existing);
    }

    @Test
    @DisplayName("DELETE /vendors/{id}: returns 404 for an unknown vendor and deletes nothing")
    void deleteVendor_notFound() throws Exception {
        when(vendorRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/vendors/vendors/999"))
                .andExpect(status().isNotFound());

        verify(vendorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE /vendors/{id}: returns 403 when an IMMIGRATION user tries to delete a vendor")
    void deleteVendor_forbiddenForImmigration() throws Exception {
        CurrentUserContext.set("immi@example.com", "IMMIGRATION");

        mockMvc.perform(delete("/api/v1/vendors/vendors/1"))
                .andExpect(status().isForbidden());

        verify(vendorRepository, never()).delete(any());
    }
}
