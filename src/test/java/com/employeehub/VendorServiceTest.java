package com.employeehub;

import com.employeehub.model.Vendor;
import com.employeehub.repository.VendorRepository;
import com.employeehub.service.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Vendor onboarding save (NewVendor.jsx -> POST
 * /vendors/saveOnBoardDetails). The status default matters: a brand new
 * vendor must start Pending, not Active, so nobody can be paid before being
 * approved.
 */
@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @InjectMocks
    private VendorService vendorService;

    private ArgumentCaptor<Vendor> savedVendor;

    @BeforeEach
    void setUp() {
        savedVendor = ArgumentCaptor.forClass(Vendor.class);
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private com.employeehub.domain.Vendor request(String status, String street, String street2,
                                                  String city, String state, String zip, String country) {
        return new com.employeehub.domain.Vendor(
                null,                           // vendorId
                "Sam Supplier",                 // vendorName
                "98-7654321",                   // ein
                "5559876543",                   // phone
                "sam@supplier.test",            // emailId
                LocalDate.of(2026, 2, 1),       // startDate
                LocalDate.of(2027, 1, 31),      // endDate
                "Supplier Inc",                 // vendorCompanyName
                "https://supplier.test",        // webSite
                street, street2, city, state, zip, country,
                "Staffing",                     // vendorType
                "Net 45",                       // paymentTerms
                "Pay on receipt",               // paymentPolicy
                status                          // status
        );
    }

    private com.employeehub.domain.Vendor fullRequest(String status) {
        return request(status, "500 Oak Ave", "Floor 2", "Austin", "TX", "73301", "USA");
    }

    private Vendor save(com.employeehub.domain.Vendor req) {
        vendorService.saveVendor(req);
        verify(vendorRepository).save(savedVendor.capture());
        return savedVendor.getValue();
    }

    // ── field mapping ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveVendor: maps the business identity fields onto the entity")
    void saveVendor_mapsIdentityFields() {
        Vendor saved = save(fullRequest("Active"));

        assertEquals("Supplier Inc", saved.getVendorCompanyName());
        assertEquals("Sam Supplier", saved.getVendorName());
        assertEquals("98-7654321", saved.getEin());
        assertEquals("https://supplier.test", saved.getWebsite());
    }

    @Test
    @DisplayName("saveVendor: maps the contact fields, using the primary email for both email columns")
    void saveVendor_mapsContactFields() {
        Vendor saved = save(fullRequest("Active"));

        assertEquals("sam@supplier.test", saved.getVendorEmail());
        assertEquals("sam@supplier.test", saved.getVendorContactEmail());
        assertEquals("5559876543", saved.getVendorPhone());
    }

    @Test
    @DisplayName("saveVendor: maps the vendor type and payment terms")
    void saveVendor_mapsTypeAndTerms() {
        Vendor saved = save(fullRequest("Active"));

        assertEquals("Staffing", saved.getVendorType());
        assertEquals("Net 45", saved.getPaymentTerms());
        assertEquals("Pay on receipt", saved.getPaymentPolicy());
    }

    @Test
    @DisplayName("saveVendor: maps the start and end dates")
    void saveVendor_mapsDates() {
        Vendor saved = save(fullRequest("Active"));

        assertEquals(LocalDate.of(2026, 2, 1), saved.getVendorStartDate());
        assertEquals(LocalDate.of(2027, 1, 31), saved.getVendorEndDate());
    }

    @Test
    @DisplayName("saveVendor: stamps lastUpdated with today's date")
    void saveVendor_stampsLastUpdated() {
        assertEquals(LocalDate.now(), save(fullRequest("Active")).getLastUpdated());
    }

    // ── status default ──────────────────────────────────────────────────────

    @Test
    @DisplayName("saveVendor: keeps the status the user picked on the form")
    void saveVendor_keepsSuppliedStatus() {
        assertEquals("On Hold", save(fullRequest("On Hold")).getVendorStatus());
    }

    @Test
    @DisplayName("saveVendor: defaults to Pending (never Active) when no status was supplied")
    void saveVendor_defaultsStatusToPending() {
        assertEquals("Pending", save(fullRequest(null)).getVendorStatus());
    }

    @Test
    @DisplayName("saveVendor: returns the persisted vendor")
    void saveVendor_returnsSavedVendor() {
        assertTrue(vendorService.saveVendor(fullRequest("Active")).isPresent());
    }

    // ── address flattening ──────────────────────────────────────────────────

    @Test
    @DisplayName("saveVendor: joins every address component into one comma-separated string")
    void formatAddress_joinsAllComponents() {
        Vendor saved = save(fullRequest("Active"));

        assertEquals("500 Oak Ave, Floor 2, Austin, TX 73301, USA", saved.getVendorAddress());
    }

    @Test
    @DisplayName("saveVendor: omits a missing address line 2 rather than leaving a stray comma")
    void formatAddress_omitsNullStreet2() {
        Vendor saved = save(request("Active", "500 Oak Ave", null, "Austin", "TX", "73301", "USA"));

        assertEquals("500 Oak Ave, Austin, TX 73301, USA", saved.getVendorAddress());
    }

    @Test
    @DisplayName("saveVendor: treats a whitespace-only address component as missing")
    void formatAddress_omitsBlankComponents() {
        Vendor saved = save(request("Active", "500 Oak Ave", "  ", "Austin", "TX", "73301", "USA"));

        assertEquals("500 Oak Ave, Austin, TX 73301, USA", saved.getVendorAddress());
    }

    @Test
    @DisplayName("saveVendor: pairs state and zip with a space, not a comma")
    void formatAddress_joinsStateAndZipWithSpace() {
        Vendor saved = save(request("Active", null, null, null, "TX", "73301", null));

        assertEquals("TX 73301", saved.getVendorAddress());
    }

    @Test
    @DisplayName("saveVendor: keeps a zip with no state")
    void formatAddress_zipWithoutState() {
        Vendor saved = save(request("Active", null, null, "Austin", null, "73301", null));

        assertEquals("Austin, 73301", saved.getVendorAddress());
    }

    @Test
    @DisplayName("saveVendor: trims surrounding whitespace from each address component")
    void formatAddress_trimsComponents() {
        Vendor saved = save(request("Active", " 500 Oak Ave ", null, " Austin ", " TX ", " 73301 ", " USA "));

        assertEquals("500 Oak Ave, Austin, TX 73301, USA", saved.getVendorAddress());
    }

    @Test
    @DisplayName("saveVendor: stores an empty address string when no address was supplied at all")
    void formatAddress_emptyWhenNoComponents() {
        Vendor saved = save(request("Active", null, null, null, null, null, null));

        assertEquals("", saved.getVendorAddress());
    }
}
