package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.BillsController;
import com.employeehub.domain.BasicEmployee;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Bills;
import com.employeehub.model.Invoice;
import com.employeehub.repository.BillsRepository;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.service.BillsService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bills endpoints. Writes are ADMIN/ACCOUNTING only; the read endpoints also
 * decorate each bill with the employee's name and the invoice's human-readable
 * number, which is where most of the per-endpoint logic lives.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillsControllerTest {

    @Mock
    private BillsRepository billsRepository;

    @Mock
    private BillsService billsService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private BillsController billsController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(billsController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        CurrentUserContext.set("acct@example.com", "ACCOUNTING");
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private Bills bill(long id, Long invoiceId, long employeeId, String status, float hours, float total) {
        Bills b = new Bills();
        b.setBillId(id);
        b.setInvoiceId(invoiceId);
        b.setProjectId(7L);
        b.setEmployeeId(employeeId);
        b.setStatus(status);
        b.setHours(hours);
        b.setTotal(total);
        b.setBilling(50f);
        b.setInvoiceMonth(LocalDate.of(2026, 6, 1));
        b.setStartDate(LocalDate.of(2026, 6, 1));
        b.setEndDate(LocalDate.of(2026, 6, 30));
        return b;
    }

    private Invoice invoice(long id, long invoiceNumber) {
        Invoice i = new Invoice();
        i.setId(id);
        i.setInvoiceNumber(invoiceNumber);
        return i;
    }

    // ── POST / (create) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /bills: returns 201 with the created bill")
    void createBill_returns201() throws Exception {
        when(billsService.createBill(any())).thenReturn(bill(1L, 100L, 11L, "Created", 160f, 8000f));

        mockMvc.perform(post("/api/v1/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bill(0L, 100L, 11L, "Created", 160f, 8000f))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.billId").value(1))
                .andExpect(jsonPath("$.status").value("Created"));
    }

    @Test
    @DisplayName("POST /bills: returns 403 when an HR user tries to create a bill")
    void createBill_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(post("/api/v1/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bill(0L, 100L, 11L, "Created", 160f, 8000f))))
                .andExpect(status().isForbidden());

        verify(billsService, never()).createBill(any());
    }

    @Test
    @DisplayName("POST /bills: returns 400 for a malformed JSON body")
    void createBill_badRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /bills/{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /bills/{id}: returns 200 with the bill")
    void getBillById_found() throws Exception {
        when(billsService.getBillById(1L)).thenReturn(bill(1L, 100L, 11L, "Created", 160f, 8000f));

        mockMvc.perform(get("/api/v1/bills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billId").value(1));
    }

    @Test
    @DisplayName("GET /bills/{id}: returns 404 for an unknown bill")
    void getBillById_notFound() throws Exception {
        when(billsService.getBillById(999L)).thenThrow(new ResourceNotFoundException("Bill not exist with id: 999"));

        mockMvc.perform(get("/api/v1/bills/999"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /bills/{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /bills/{id}: returns 200 with the updated bill")
    void updateBill_returns200() throws Exception {
        when(billsService.updateBill(eq(1L), any())).thenReturn(bill(1L, 100L, 11L, "Paid", 160f, 8000f));

        mockMvc.perform(put("/api/v1/bills/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bill(1L, 100L, 11L, "Paid", 160f, 8000f))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Paid"));
    }

    @Test
    @DisplayName("PUT /bills/{id}: returns 404 for an unknown bill")
    void updateBill_notFound() throws Exception {
        when(billsService.updateBill(eq(999L), any()))
                .thenThrow(new ResourceNotFoundException("Bill not exist with id: 999"));

        mockMvc.perform(put("/api/v1/bills/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bill(0L, 100L, 11L, "Paid", 160f, 8000f))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /bills/{id}: returns 403 when an HR user tries to edit a bill")
    void updateBill_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(put("/api/v1/bills/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bill(1L, 100L, 11L, "Paid", 160f, 8000f))))
                .andExpect(status().isForbidden());

        verify(billsService, never()).updateBill(anyLong(), any());
    }

    // ── DELETE /bills/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /bills/{id}: returns 204 and deletes the bill")
    void deleteBill_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/bills/1"))
                .andExpect(status().isNoContent());

        verify(billsService).deleteBill(1L);
    }

    @Test
    @DisplayName("DELETE /bills/{id}: returns 404 for an unknown bill")
    void deleteBill_notFound() throws Exception {
        doThrow(new ResourceNotFoundException("Bill not exist with id: 999")).when(billsService).deleteBill(999L);

        mockMvc.perform(delete("/api/v1/bills/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /bills/{id}: returns 403 when an IMMIGRATION user tries to delete a bill")
    void deleteBill_forbiddenForImmigration() throws Exception {
        CurrentUserContext.set("immi@example.com", "IMMIGRATION");

        mockMvc.perform(delete("/api/v1/bills/1"))
                .andExpect(status().isForbidden());

        verify(billsService, never()).deleteBill(anyLong());
    }

    // ── read endpoints ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /getAllBills: returns 200 with every bill")
    void getAllBills_returnsList() throws Exception {
        when(billsRepository.findAll()).thenReturn(List.of(
                bill(1L, 100L, 11L, "Created", 160f, 8000f),
                bill(2L, 100L, 12L, "Paid", 80f, 4000f)));

        mockMvc.perform(get("/api/v1/bills/getAllBills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /getBillsForCustomer: returns 200 with the customer's bills")
    void getBillsForCustomer_returnsList() throws Exception {
        when(billsRepository.findByCustomer(21L)).thenReturn(List.of(bill(1L, 100L, 11L, "Created", 160f, 8000f)));

        mockMvc.perform(get("/api/v1/bills/getBillsForCustomer").param("customerId", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /getBillsForCustomer: returns 400 when customerId is missing")
    void getBillsForCustomer_badRequestWithoutCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/bills/getBillsForCustomer"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /getBillsForMonthAndYear: converts the ISO date into the yyyyMM lookup key")
    void getBillsForMonthAndYear_convertsToMonthKey() throws Exception {
        when(billsService.findBillsByInvoiceMonth("202606"))
                .thenReturn(Optional.of(List.of(bill(1L, 100L, 11L, "Created", 160f, 8000f))));

        mockMvc.perform(get("/api/v1/bills/getBillsForMonthAndYear").param("selectedDate", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(billsService).findBillsByInvoiceMonth("202606");
    }

    @Test
    @DisplayName("GET /getBillsForMonthAndYear: rejects a selectedDate that is not ISO formatted")
    void getBillsForMonthAndYear_rejectsBadDate() {
        // No global @ExceptionHandler is registered, so the parse failure
        // propagates out of the dispatcher wrapped in NestedServletException
        // (same convention as EmployeeControllerSaveTest). Worth noting that a
        // caller therefore sees a 500, not a 400, for a badly formatted date.
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.util.NestedServletException.class,
                () -> mockMvc.perform(get("/api/v1/bills/getBillsForMonthAndYear")
                        .param("selectedDate", "06/15/2026")));
    }

    @Test
    @DisplayName("GET /getBillsForProject: decorates each bill with the employee's name")
    void getBillsForProject_addsEmployeeName() throws Exception {
        when(billsService.findBillsForProject(7L))
                .thenReturn(Optional.of(List.of(bill(1L, 100L, 11L, "Created", 160f, 8000f))));
        when(employeeService.getEmployees())
                .thenReturn(List.of(new BasicEmployee(BigInteger.valueOf(11), "Ada Lovelace", "Active")));
        when(invoiceRepository.findAllById(List.of(100L))).thenReturn(List.of(invoice(100L, 901L)));

        mockMvc.perform(get("/api/v1/bills/getBillsForProject").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeName").value("Ada Lovelace"));
    }

    @Test
    @DisplayName("GET /getBillsForProject: shows Unknown when the bill's employee no longer exists")
    void getBillsForProject_unknownEmployeeName() throws Exception {
        when(billsService.findBillsForProject(7L))
                .thenReturn(Optional.of(List.of(bill(1L, 100L, 999L, "Created", 160f, 8000f))));
        when(employeeService.getEmployees())
                .thenReturn(List.of(new BasicEmployee(BigInteger.valueOf(11), "Ada Lovelace", "Active")));
        when(invoiceRepository.findAllById(List.of(100L))).thenReturn(List.of(invoice(100L, 901L)));

        mockMvc.perform(get("/api/v1/bills/getBillsForProject").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeName").value("Unknown"));
    }

    @Test
    @DisplayName("GET /getBillsForProject: resolves each bill's invoice number from its invoice id")
    void getBillsForProject_resolvesInvoiceNumber() throws Exception {
        when(billsService.findBillsForProject(7L))
                .thenReturn(Optional.of(List.of(bill(1L, 100L, 11L, "Created", 160f, 8000f))));
        when(employeeService.getEmployees())
                .thenReturn(List.of(new BasicEmployee(BigInteger.valueOf(11), "Ada Lovelace", "Active")));
        when(invoiceRepository.findAllById(List.of(100L))).thenReturn(List.of(invoice(100L, 901L)));

        mockMvc.perform(get("/api/v1/bills/getBillsForProject").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNumber").value(901));
    }

    @Test
    @DisplayName("GET /getBillsForEmployee: returns the employee's bills with their name attached")
    void getBillsForEmployee_returnsDecoratedBills() throws Exception {
        when(billsService.findBillsForEmployee(11L))
                .thenReturn(Optional.of(List.of(bill(1L, 100L, 11L, "Created", 160f, 8000f))));
        when(employeeService.getEmployees())
                .thenReturn(List.of(new BasicEmployee(BigInteger.valueOf(11), "Ada Lovelace", "Active")));
        when(invoiceRepository.findAllById(List.of(100L))).thenReturn(List.of(invoice(100L, 901L)));

        mockMvc.perform(get("/api/v1/bills/getBillsForEmployee").param("employeeId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeName").value("Ada Lovelace"));
    }

    @Test
    @DisplayName("GET /getBillsForEmployee: returns 200 with an empty array when the employee has no bills")
    void getBillsForEmployee_emptyList() throws Exception {
        when(billsService.findBillsForEmployee(11L)).thenReturn(Optional.of(List.of()));
        when(employeeService.getEmployees()).thenReturn(List.of());
        when(invoiceRepository.findAllById(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bills/getBillsForEmployee").param("employeeId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
