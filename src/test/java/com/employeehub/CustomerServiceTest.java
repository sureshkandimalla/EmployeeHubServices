package com.employeehub;

import com.employeehub.model.Customer;
import com.employeehub.repository.CustomerRepository;
import com.employeehub.service.CustomerService;
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
 * Covers the Customer onboarding save (NewCustomer.jsx -> POST
 * /customers/saveOnBoardDetails), including the single-column address string
 * the form's separate address fields are flattened into.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private ArgumentCaptor<Customer> savedCustomer;

    @BeforeEach
    void setUp() {
        savedCustomer = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Full onboarding request; address components are the per-test variable. */
    private com.employeehub.domain.Customer request(String street, String street2, String city,
                                                    String state, String zip, String country) {
        return new com.employeehub.domain.Customer(
                null,                           // customerId
                "Jane Buyer",                   // customerName
                "12-3456789",                   // ein
                "5551234567",                   // phone
                "jane@acme.test",               // emailId
                LocalDate.of(2026, 1, 1),       // startDate
                LocalDate.of(2026, 12, 31),     // endDate
                "Acme Corp",                    // customerCompanyName
                "https://acme.test",            // webSite
                street, street2, city, state, zip, country,
                50000.0,                        // creditLimit
                "Acme Holdings",                // parentCompany
                "billing@acme.test",            // billingContact
                "ap@acme.test",                 // apContact
                "USD",                          // standardCurrency
                "Signed",                       // msaStatus
                "ACH",                          // defaultBillingMethod
                "Net 30",                       // paymentTerms
                "a note"                        // notes
        );
    }

    private com.employeehub.domain.Customer fullRequest() {
        return request("123 Main St", "Suite 400", "Dallas", "TX", "75001", "USA");
    }

    private Customer save(com.employeehub.domain.Customer req) {
        customerService.saveCustomer(req);
        verify(customerRepository).save(savedCustomer.capture());
        return savedCustomer.getValue();
    }

    // ── field mapping ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveCustomer: maps the business identity fields onto the entity")
    void saveCustomer_mapsIdentityFields() {
        Customer saved = save(fullRequest());

        assertEquals("Acme Corp", saved.getCustomerCompanyName());
        assertEquals("Jane Buyer", saved.getCustomerName());
        assertEquals("12-3456789", saved.getEin());
        assertEquals("https://acme.test", saved.getWebsite());
        assertEquals("Acme Holdings", saved.getParentCompany());
    }

    @Test
    @DisplayName("saveCustomer: maps the contact fields, using the primary email for both email columns")
    void saveCustomer_mapsContactFields() {
        Customer saved = save(fullRequest());

        assertEquals("jane@acme.test", saved.getCustomerEmail());
        assertEquals("jane@acme.test", saved.getCustomerContactEmail());
        assertEquals("5551234567", saved.getCustomerPhone());
        assertEquals("billing@acme.test", saved.getBillingContact());
        assertEquals("ap@acme.test", saved.getApContact());
    }

    @Test
    @DisplayName("saveCustomer: maps the commercial terms fields")
    void saveCustomer_mapsCommercialTerms() {
        Customer saved = save(fullRequest());

        assertEquals(50000.0, saved.getCreditLimit());
        assertEquals("USD", saved.getStandardCurrency());
        assertEquals("Signed", saved.getMsaStatus());
        assertEquals("ACH", saved.getDefaultBillingMethod());
        assertEquals("Net 30", saved.getPaymentTerms());
        assertEquals("a note", saved.getNotes());
    }

    @Test
    @DisplayName("saveCustomer: maps the start and end dates")
    void saveCustomer_mapsDates() {
        Customer saved = save(fullRequest());

        assertEquals(LocalDate.of(2026, 1, 1), saved.getCustomerStartDate());
        assertEquals(LocalDate.of(2026, 12, 31), saved.getCustomerEndDate());
    }

    @Test
    @DisplayName("saveCustomer: a new customer always starts Active")
    void saveCustomer_defaultsStatusToActive() {
        assertEquals("Active", save(fullRequest()).getCustomerStatus());
    }

    @Test
    @DisplayName("saveCustomer: stamps lastUpdated with today's date")
    void saveCustomer_stampsLastUpdated() {
        assertEquals(LocalDate.now(), save(fullRequest()).getLastUpdated());
    }

    @Test
    @DisplayName("saveCustomer: returns the persisted customer")
    void saveCustomer_returnsSavedCustomer() {
        assertTrue(customerService.saveCustomer(fullRequest()).isPresent());
    }

    // ── address flattening ──────────────────────────────────────────────────

    @Test
    @DisplayName("saveCustomer: joins every address component into one comma-separated string")
    void formatAddress_joinsAllComponents() {
        Customer saved = save(fullRequest());

        assertEquals("123 Main St, Suite 400, Dallas, TX 75001, USA", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: omits a missing address line 2 rather than leaving a stray comma")
    void formatAddress_omitsNullStreet2() {
        Customer saved = save(request("123 Main St", null, "Dallas", "TX", "75001", "USA"));

        assertEquals("123 Main St, Dallas, TX 75001, USA", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: treats a whitespace-only address component as missing")
    void formatAddress_omitsBlankComponents() {
        Customer saved = save(request("123 Main St", "   ", "Dallas", "TX", "75001", "USA"));

        assertEquals("123 Main St, Dallas, TX 75001, USA", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: pairs state and zip with a space, not a comma")
    void formatAddress_joinsStateAndZipWithSpace() {
        Customer saved = save(request(null, null, null, "TX", "75001", null));

        assertEquals("TX 75001", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: keeps a state with no zip")
    void formatAddress_stateWithoutZip() {
        Customer saved = save(request(null, null, "Dallas", "TX", null, null));

        assertEquals("Dallas, TX", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: keeps a zip with no state")
    void formatAddress_zipWithoutState() {
        Customer saved = save(request(null, null, "Dallas", null, "75001", null));

        assertEquals("Dallas, 75001", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: trims surrounding whitespace from each address component")
    void formatAddress_trimsComponents() {
        Customer saved = save(request("  123 Main St  ", null, " Dallas ", " TX ", " 75001 ", " USA "));

        assertEquals("123 Main St, Dallas, TX 75001, USA", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: stores an empty address string when no address was supplied at all")
    void formatAddress_emptyWhenNoComponents() {
        Customer saved = save(request(null, null, null, null, null, null));

        assertEquals("", saved.getCustomerAddress());
    }

    @Test
    @DisplayName("saveCustomer: a customer with no address still saves")
    void saveCustomer_savesWithoutAddress() {
        assertTrue(customerService.saveCustomer(request(null, null, null, null, null, null)).isPresent());
    }
}
