package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.CustomerController;
import com.employeehub.model.Customer;
import com.employeehub.repository.CustomerRepository;
import com.employeehub.service.CustomerService;
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
 * Customer endpoints: who may write (ADMIN/ACCOUNTING only), what a missing
 * record returns, and that the PUT really is a partial update — a client
 * sending only one field must not blank out the rest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerControllerTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private CustomerController customerController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(customerController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        CurrentUserContext.set("acct@example.com", "ACCOUNTING");
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private Customer customer(long id, String companyName) {
        Customer c = new Customer();
        c.setCustomerId(id);
        c.setCustomerCompanyName(companyName);
        c.setCustomerName("Jane Buyer");
        c.setCustomerEmail("jane@acme.test");
        c.setCustomerStatus("Active");
        c.setCustomerStartDate(LocalDate.of(2026, 1, 1));
        c.setPaymentTerms("Net 30");
        return c;
    }

    private com.employeehub.domain.Customer onboardRequest() {
        return new com.employeehub.domain.Customer(
                null, "Jane Buyer", "12-3456789", "5551234567", "jane@acme.test",
                LocalDate.of(2026, 1, 1), null, "Acme Corp", "https://acme.test",
                "123 Main St", null, "Dallas", "TX", "75001", "USA",
                50000.0, null, null, null, "USD", "Signed", "ACH", "Net 30", null);
    }

    // ── GET /getAllCustomers ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /getAllCustomers: returns 200 with every customer")
    void getAllCustomers_returnsList() throws Exception {
        when(customerRepository.findAll()).thenReturn(List.of(customer(1L, "Acme Corp"), customer(2L, "Globex")));

        mockMvc.perform(get("/api/v1/customers/getAllCustomers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].customerCompanyName").value("Acme Corp"));
    }

    @Test
    @DisplayName("GET /getAllCustomers: returns 200 with an empty array when there are no customers")
    void getAllCustomers_emptyList() throws Exception {
        when(customerRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customers/getAllCustomers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── POST /saveOnBoardDetails ────────────────────────────────────────────

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 200 with the saved customer for an ACCOUNTING user")
    void createCustomer_savesForAccounting() throws Exception {
        when(customerService.saveCustomer(any())).thenReturn(Optional.of(customer(1L, "Acme Corp")));

        mockMvc.perform(post("/api/v1/customers/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.customerCompanyName").value("Acme Corp"));
    }

    @Test
    @DisplayName("POST /saveOnBoardDetails: an ADMIN user may also create a customer")
    void createCustomer_savesForAdmin() throws Exception {
        CurrentUserContext.set("admin@example.com", "ADMIN");
        when(customerService.saveCustomer(any())).thenReturn(Optional.of(customer(1L, "Acme Corp")));

        mockMvc.perform(post("/api/v1/customers/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 403 when an HR user tries to create a customer")
    void createCustomer_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(post("/api/v1/customers/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardRequest())))
                .andExpect(status().isForbidden());

        verify(customerService, never()).saveCustomer(any());
    }

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 403 when the caller has no role assigned")
    void createCustomer_forbiddenWithoutRole() throws Exception {
        CurrentUserContext.set("nobody@example.com", null);

        mockMvc.perform(post("/api/v1/customers/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /saveOnBoardDetails: returns 400 for a malformed JSON body")
    void createCustomer_badRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/customers/saveOnBoardDetails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /customers/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /customers/{id}: returns 200 with the customer")
    void getCustomerById_found() throws Exception {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "Acme Corp")));

        mockMvc.perform(get("/api/v1/customers/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1));
    }

    @Test
    @DisplayName("GET /customers/{id}: returns 404 for an unknown customer")
    void getCustomerById_notFound() throws Exception {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/customers/customers/999"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /customers/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /customers/{id}: returns 200 and applies the supplied fields")
    void updateCustomer_appliesSuppliedFields() throws Exception {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "Acme Corp")));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer body = new Customer();
        body.setCustomerCompanyName("Acme Corporation");
        body.setCustomerStatus("Inactive");

        mockMvc.perform(put("/api/v1/customers/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerCompanyName").value("Acme Corporation"))
                .andExpect(jsonPath("$.customerStatus").value("Inactive"));
    }

    @Test
    @DisplayName("PUT /customers/{id}: fields omitted from the body keep their stored values")
    void updateCustomer_isPartialUpdate() throws Exception {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "Acme Corp")));
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer body = new Customer();
        body.setCustomerStatus("Inactive");

        mockMvc.perform(put("/api/v1/customers/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(customerRepository).save(captor.capture());
        assertEquals("Net 30", captor.getValue().getPaymentTerms());
        assertEquals("jane@acme.test", captor.getValue().getCustomerEmail());
        assertEquals(LocalDate.of(2026, 1, 1), captor.getValue().getCustomerStartDate());
    }

    @Test
    @DisplayName("PUT /customers/{id}: returns 404 for an unknown customer and saves nothing")
    void updateCustomer_notFound() throws Exception {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/customers/customers/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Customer())))
                .andExpect(status().isNotFound());

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT /customers/{id}: returns 403 when an HR user tries to edit a customer")
    void updateCustomer_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(put("/api/v1/customers/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Customer())))
                .andExpect(status().isForbidden());

        verify(customerRepository, never()).save(any());
    }

    // ── DELETE /customers/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /customers/{id}: returns 200 with deleted=true and removes the customer")
    void deleteCustomer_deletes() throws Exception {
        Customer existing = customer(1L, "Acme Corp");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/v1/customers/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(customerRepository).delete(existing);
    }

    @Test
    @DisplayName("DELETE /customers/{id}: returns 404 for an unknown customer and deletes nothing")
    void deleteCustomer_notFound() throws Exception {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/customers/customers/999"))
                .andExpect(status().isNotFound());

        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE /customers/{id}: returns 403 when an IMMIGRATION user tries to delete a customer")
    void deleteCustomer_forbiddenForImmigration() throws Exception {
        CurrentUserContext.set("immi@example.com", "IMMIGRATION");

        mockMvc.perform(delete("/api/v1/customers/customers/1"))
                .andExpect(status().isForbidden());

        verify(customerRepository, never()).delete(any());
    }
}
