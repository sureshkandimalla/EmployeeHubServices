package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.InvoiceController;
import com.employeehub.model.Invoice;
import com.employeehub.repository.AssignmentRepository;
import com.employeehub.repository.InvoiceRepository;
import com.employeehub.service.InvoiceService;
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
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Invoice endpoints — every mutation is ADMIN/ACCOUNTING only, and the PUT
 * carries the paid-amount/paid-date reconciliation plus the bill status
 * cascade, which is the money-critical part.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceControllerTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private InvoiceController invoiceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(invoiceController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        CurrentUserContext.set("acct@example.com", "ACCOUNTING");
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private Invoice invoice(Long id, Long invoiceNumber, String status, float total,
                            float discounts, float paidAmount, LocalDate paidDate) {
        Invoice i = new Invoice();
        i.setId(id);
        i.setInvoiceNumber(invoiceNumber);
        i.setProjectId(7L);
        i.setStatus(status);
        i.setHours(160f);
        i.setTotal(total);
        i.setDiscounts(discounts);
        i.setInvoicePaidAmount(paidAmount);
        i.setInvoicePaidDate(paidDate);
        i.setStartDate(LocalDate.of(2026, 6, 1));
        i.setEndDate(LocalDate.of(2026, 6, 30));
        i.setInvoiceMonth(LocalDate.of(2026, 6, 1));
        return i;
    }

    private String domainInvoiceJson(Long invoiceNumber) {
        return "[{\"invoiceNumber\":" + invoiceNumber + ",\"projectId\":7,\"hours\":160.0,"
                + "\"total\":8000.0,\"billRate\":50.0,\"startDate\":\"2026-06-01\","
                + "\"endDate\":\"2026-06-30\",\"formatSelectedDate\":\"2026-06-01\"}]";
    }

    // ── POST /addInvoices ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /addInvoices: returns 201 and creates each submitted invoice")
    void addInvoices_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/invoice/addInvoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(domainInvoiceJson(501L)))
                .andExpect(status().isCreated());

        verify(invoiceService).createInvoiceObject(any(), eq("2026-06-01"));
    }

    @Test
    @DisplayName("POST /addInvoices: silently skips a row with no invoice number")
    void addInvoices_skipsRowsWithoutInvoiceNumber() throws Exception {
        mockMvc.perform(post("/api/v1/invoice/addInvoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(domainInvoiceJson(null)))
                .andExpect(status().isCreated());

        verify(invoiceService, never()).createInvoiceObject(any(), any());
    }

    @Test
    @DisplayName("POST /addInvoices: silently skips a row whose invoice number is zero")
    void addInvoices_skipsZeroInvoiceNumber() throws Exception {
        mockMvc.perform(post("/api/v1/invoice/addInvoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(domainInvoiceJson(0L)))
                .andExpect(status().isCreated());

        verify(invoiceService, never()).createInvoiceObject(any(), any());
    }

    @Test
    @DisplayName("POST /addInvoices: returns 403 when an HR user tries to create invoices")
    void addInvoices_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(post("/api/v1/invoice/addInvoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(domainInvoiceJson(501L)))
                .andExpect(status().isForbidden());

        verify(invoiceService, never()).createInvoiceObject(any(), any());
    }

    @Test
    @DisplayName("POST /addInvoices: returns 403 when the caller has no role assigned")
    void addInvoices_forbiddenWithoutRole() throws Exception {
        CurrentUserContext.set("nobody@example.com", null);

        mockMvc.perform(post("/api/v1/invoice/addInvoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(domainInvoiceJson(501L)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /addInvoices: returns 400 for a malformed JSON body")
    void addInvoices_badRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/invoice/addInvoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{"))
                .andExpect(status().isBadRequest());
    }

    // ── isValid ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isValid: accepts a real invoice number and rejects the null/zero/MIN placeholders")
    void isValid_filtersPlaceholders() {
        assertEquals(true, invoiceController.isValid(42L));
        assertEquals(false, invoiceController.isValid(null));
        assertEquals(false, invoiceController.isValid(0L));
        assertEquals(false, invoiceController.isValid(Long.MIN_VALUE));
    }

    // ── GET /invoices/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /invoices/{id}: returns 200 with the invoice")
    void getInvoiceById_found() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));

        mockMvc.perform(get("/api/v1/invoice/invoices/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.invoiceNumber").value(501));
    }

    @Test
    @DisplayName("GET /invoices/{id}: returns 404 for an unknown invoice")
    void getInvoiceById_notFound() throws Exception {
        when(invoiceRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/invoice/invoices/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /getAllInvoices ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /getAllInvoices: returns 200 with every invoice")
    void getAllInvoices_returnsList() throws Exception {
        when(invoiceRepository.getAllInvoices()).thenReturn(List.of(
                invoice(100L, 501L, "Created", 8000f, 0f, 0f, null),
                invoice(101L, 502L, "Paid", 4000f, 0f, 4000f, LocalDate.of(2026, 7, 1))));

        mockMvc.perform(get("/api/v1/invoice/getAllInvoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── GET /invoicesForMonthAndYear ────────────────────────────────────────

    @Test
    @DisplayName("GET /invoicesForMonthAndYear: status=viewAll returns every invoice for the month")
    void invoicesForMonthAndYear_viewAll() throws Exception {
        when(invoiceRepository.findAllInvoicesForTheMonth("202606"))
                .thenReturn(List.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));

        mockMvc.perform(get("/api/v1/invoice/invoicesForMonthAndYear")
                        .param("selectedDate", "2026-06-15")
                        .param("status", "viewAll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /invoicesForMonthAndYear: a concrete status filters the month's invoices by it")
    void invoicesForMonthAndYear_filtersByStatus() throws Exception {
        when(invoiceRepository.findAllInvoicesForTheMonthAndStatus("202606", "Paid"))
                .thenReturn(List.of(invoice(101L, 502L, "Paid", 4000f, 0f, 4000f, LocalDate.of(2026, 7, 1))));

        mockMvc.perform(get("/api/v1/invoice/invoicesForMonthAndYear")
                        .param("selectedDate", "2026-06-15")
                        .param("status", "Paid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("Paid"));
    }

    @Test
    @DisplayName("GET /invoicesForMonthAndYear: omitting the optional status returns the month's invoices")
    void invoicesForMonthAndYear_statusIsOptional() throws Exception {
        when(invoiceRepository.findAllInvoicesForTheMonth("202606"))
                .thenReturn(List.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));

        mockMvc.perform(get("/api/v1/invoice/invoicesForMonthAndYear")
                        .param("selectedDate", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ── PUT /invoices/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /invoices/{id}: returns 200 with the updated invoice")
    void updateInvoice_returns200() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Created", 8500f, 0f, 0f, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8500.0));
    }

    @Test
    @DisplayName("PUT /invoices/{id}: the path id wins over any id in the request body")
    void updateInvoice_pathIdWinsOverBodyId() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(777L, 501L, "Created", 8000f, 0f, 0f, null))))
                .andExpect(status().isOk());

        verify(invoiceRepository).saveAndFlush(captor.capture());
        assertEquals(100L, captor.getValue().getId());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: marking an invoice Paid with no amount pays total minus discounts")
    void updateInvoice_paidDefaultsPaidAmountToTotalMinusDiscounts() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Paid", 8000f, 500f, 0f, null))))
                .andExpect(status().isOk());

        verify(invoiceRepository).saveAndFlush(captor.capture());
        assertEquals(7500f, captor.getValue().getInvoicePaidAmount());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: a discount larger than the total never produces a negative paid amount")
    void updateInvoice_paidAmountNeverGoesNegative() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Paid", 1000f, 5000f, 0f, null))))
                .andExpect(status().isOk());

        verify(invoiceRepository).saveAndFlush(captor.capture());
        assertEquals(0f, captor.getValue().getInvoicePaidAmount());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: a Paid invoice with no paid date is stamped with today's date")
    void updateInvoice_paidStampsPaidDate() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Paid", 8000f, 0f, 0f, null))))
                .andExpect(status().isOk());

        verify(invoiceRepository).saveAndFlush(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue().getInvoicePaidDate());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: a paid date the user supplied is kept as-is")
    void updateInvoice_keepsSuppliedPaidDate() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Paid", 8000f, 0f, 8000f, LocalDate.of(2026, 7, 5)))))
                .andExpect(status().isOk());

        verify(invoiceRepository).saveAndFlush(captor.capture());
        assertEquals(LocalDate.of(2026, 7, 5), captor.getValue().getInvoicePaidDate());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: moving an invoice back to Unpaid clears its paid amount")
    void updateInvoice_unpaidClearsPaidAmount() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Paid", 8000f, 0f, 8000f, LocalDate.of(2026, 7, 1))));
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Unpaid", 8000f, 0f, 8000f, LocalDate.of(2026, 7, 1)))))
                .andExpect(status().isOk());

        verify(invoiceRepository).saveAndFlush(captor.capture());
        assertEquals(0f, captor.getValue().getInvoicePaidAmount());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: always pushes the saved hours down onto the invoice's bills")
    void updateInvoice_syncsBillHours() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Created", 8000f, 0f, 0f, null))))
                .andExpect(status().isOk());

        verify(invoiceService).syncBillsHoursForInvoice(any(Invoice.class));
    }

    @Test
    @DisplayName("PUT /invoices/{id}: marking an invoice Paid moves its bills to Invoice Cleared")
    void updateInvoice_paidCascadesToBills() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Paid", 8000f, 0f, 0f, null))))
                .andExpect(status().isOk());

        verify(invoiceService).markBillsInvoiceCleared(any(Invoice.class));
    }

    @Test
    @DisplayName("PUT /invoices/{id}: an invoice that is not Paid does not clear its bills")
    void updateInvoice_unpaidDoesNotCascadeToBills() throws Exception {
        when(invoiceRepository.findById(100L))
                .thenReturn(Optional.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Created", 8000f, 0f, 0f, null))))
                .andExpect(status().isOk());

        verify(invoiceService, never()).markBillsInvoiceCleared(any());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: returns 404 for an unknown invoice and saves nothing")
    void updateInvoice_notFound() throws Exception {
        when(invoiceRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/invoice/invoices/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(999L, 501L, "Created", 8000f, 0f, 0f, null))))
                .andExpect(status().isNotFound());

        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("PUT /invoices/{id}: returns 403 when an HR user tries to edit an invoice")
    void updateInvoice_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(put("/api/v1/invoice/invoices/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                invoice(100L, 501L, "Paid", 8000f, 0f, 0f, null))))
                .andExpect(status().isForbidden());

        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    // ── DELETE /invoices/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /invoices/{id}: returns 204 and deletes the invoice")
    void deleteInvoice_returns204() throws Exception {
        Invoice existing = invoice(100L, 501L, "Created", 8000f, 0f, 0f, null);
        when(invoiceRepository.findById(100L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/v1/invoice/invoices/100"))
                .andExpect(status().isNoContent());

        verify(invoiceRepository).delete(existing);
    }

    @Test
    @DisplayName("DELETE /invoices/{id}: returns 404 for an unknown invoice and deletes nothing")
    void deleteInvoice_notFound() throws Exception {
        when(invoiceRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/invoice/invoices/999"))
                .andExpect(status().isNotFound());

        verify(invoiceRepository, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE /invoices/{id}: returns 403 when an HR user tries to delete an invoice")
    void deleteInvoice_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(delete("/api/v1/invoice/invoices/100"))
                .andExpect(status().isForbidden());

        verify(invoiceRepository, never()).delete(any());
    }

    // ── maintenance endpoints ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /backfillBills: returns 200 with the number of invoices billed")
    void backfillBills_returnsCount() throws Exception {
        when(invoiceService.backfillBillsForExistingInvoices()).thenReturn(3);

        mockMvc.perform(post("/api/v1/invoice/backfillBills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesBilled").value(3));
    }

    @Test
    @DisplayName("POST /backfillBills: returns 403 when an HR user triggers the backfill")
    void backfillBills_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(post("/api/v1/invoice/backfillBills"))
                .andExpect(status().isForbidden());

        verify(invoiceService, never()).backfillBillsForExistingInvoices();
    }

    @Test
    @DisplayName("POST /validateBillStatus: returns 200 with the invoices checked and bills updated")
    void validateBillStatus_returnsCounts() throws Exception {
        when(invoiceService.validateBillStatusForAllInvoices())
                .thenReturn(Map.of("invoicesChecked", 10, "billsUpdated", 2));

        mockMvc.perform(post("/api/v1/invoice/validateBillStatus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesChecked").value(10))
                .andExpect(jsonPath("$.billsUpdated").value(2));
    }

    @Test
    @DisplayName("POST /validateBillDates: returns 200 with the invoices checked and bills updated")
    void validateBillDates_returnsCounts() throws Exception {
        when(invoiceService.validateBillDatesForAllInvoices())
                .thenReturn(Map.of("invoicesChecked", 10, "billsUpdated", 1));

        mockMvc.perform(post("/api/v1/invoice/validateBillDates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billsUpdated").value(1));
    }

    @Test
    @DisplayName("POST /deleteOrphanedBills: defaults to a dry run so nothing is deleted by accident")
    void deleteOrphanedBills_defaultsToDryRun() throws Exception {
        when(invoiceService.deleteOrphanedBills(true))
                .thenReturn(Map.of("billsChecked", 5, "orphanedFound", 1, "deleted", false));

        mockMvc.perform(post("/api/v1/invoice/deleteOrphanedBills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));

        verify(invoiceService).deleteOrphanedBills(true);
    }

    @Test
    @DisplayName("POST /deleteOrphanedBills: dryRun=false commits the deletion")
    void deleteOrphanedBills_commitsWhenDryRunFalse() throws Exception {
        when(invoiceService.deleteOrphanedBills(false))
                .thenReturn(Map.of("billsChecked", 5, "orphanedFound", 1, "deleted", true));

        mockMvc.perform(post("/api/v1/invoice/deleteOrphanedBills").param("dryRun", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(invoiceService).deleteOrphanedBills(false);
    }

    @Test
    @DisplayName("POST /deleteOrphanedBills: returns 403 when an HR user triggers the cleanup")
    void deleteOrphanedBills_forbiddenForHr() throws Exception {
        CurrentUserContext.set("hr@example.com", "HR");

        mockMvc.perform(post("/api/v1/invoice/deleteOrphanedBills"))
                .andExpect(status().isForbidden());

        verify(invoiceService, never()).deleteOrphanedBills(anyBoolean());
    }

    // ── per-entity lookups ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /getInvoicesForProject: returns 200 with the project's invoices")
    void getInvoicesForProject_returnsList() throws Exception {
        when(invoiceRepository.findByProjectId(7L))
                .thenReturn(List.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));

        mockMvc.perform(get("/api/v1/invoice/getInvoicesForProject").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /getInvoicesForProject: returns 400 when projectId is missing")
    void getInvoicesForProject_badRequestWithoutProjectId() throws Exception {
        mockMvc.perform(get("/api/v1/invoice/getInvoicesForProject"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /getInvoicesForEmployee: returns 200 with the employee's invoices")
    void getInvoicesForEmployee_returnsList() throws Exception {
        when(invoiceRepository.findByEmployee(11L))
                .thenReturn(List.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));

        mockMvc.perform(get("/api/v1/invoice/getInvoicesForEmployee").param("employeeId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /getInvoicesForCustomer: returns 200 with the customer's invoices")
    void getInvoicesForCustomer_returnsList() throws Exception {
        when(invoiceRepository.findByCustomer(21L))
                .thenReturn(List.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));

        mockMvc.perform(get("/api/v1/invoice/getInvoicesForCustomer").param("customerId", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /activeInvoices/{monthYear}: returns 200 with the month's active invoices")
    void activeInvoicesForMonth_returnsList() throws Exception {
        when(invoiceRepository.findAllActiveInvoicesForTheMonth("202606", "202606"))
                .thenReturn(List.of(invoice(100L, 501L, "Created", 8000f, 0f, 0f, null)));

        mockMvc.perform(get("/api/v1/invoice/activeInvoices/202606"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /invoicesCountByStatus: returns 200 with the per-status counts")
    void invoicesCountByStatus_returnsCounts() throws Exception {
        when(invoiceRepository.getInvoiceCountByStatus())
                .thenReturn(List.of(Map.of("status", "Created", "count", "4")));

        mockMvc.perform(get("/api/v1/invoice/invoicesCountByStatus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("Created"));
    }
}
