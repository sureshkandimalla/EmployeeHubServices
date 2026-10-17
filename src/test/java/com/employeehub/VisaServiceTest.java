package com.employeehub;

import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Employee;
import com.employeehub.model.LCA;
import com.employeehub.model.Visa;
import com.employeehub.repository.VisaRepository;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.repository.LCARepository;
import com.employeehub.service.VisaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisaServiceTest {

    @Mock
    private VisaRepository visaRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LCARepository lcaRepository;

    @InjectMocks
    private VisaService visaService;

    private Visa visa1;
    private Visa visa2;
    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setEmployeeId(1L);
        employee.setFirstName("John");
        employee.setLastName("Doe");

        visa1 = makeVisa(101L, "H1B", "REC-001", employee, LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1));
        visa2 = makeVisa(102L, "H1B", "REC-002", employee, LocalDate.of(2024, 6, 1), LocalDate.of(2027, 6, 1));
    }

    // ── getAllVisas ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllVisas: returns all visas from repository")
    void getAllVisas_returnsAll() {
        when(visaRepository.findAll()).thenReturn(Arrays.asList(visa1, visa2));

        List<Visa> result = visaService.getAllVisas();

        assertEquals(2, result.size());
        verify(visaRepository).findAll();
    }

    @Test
    @DisplayName("getAllVisas: returns empty list when no visas exist")
    void getAllVisas_emptyList() {
        when(visaRepository.findAll()).thenReturn(Collections.emptyList());

        List<Visa> result = visaService.getAllVisas();

        assertTrue(result.isEmpty());
    }

    // ── getVisaById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getVisaById: returns visa when found")
    void getVisaById_found() {
        when(visaRepository.findByVisaId(101L)).thenReturn(Optional.of(visa1));

        Optional<Visa> result = visaService.getVisaById(101L);

        assertTrue(result.isPresent());
        assertEquals("REC-001", result.get().getReceiptNumber());
    }

    @Test
    @DisplayName("getVisaById: returns empty Optional when not found")
    void getVisaById_notFound() {
        when(visaRepository.findByVisaId(999L)).thenReturn(Optional.empty());

        Optional<Visa> result = visaService.getVisaById(999L);

        assertFalse(result.isPresent());
    }

    // ── getVisasByEmployeeId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getVisasByEmployeeId: returns visas for the given employee")
    void getVisasByEmployeeId_returnsList() {
        when(visaRepository.findByEmployee_EmployeeId(1L)).thenReturn(Arrays.asList(visa1, visa2));

        List<Visa> result = visaService.getVisasByEmployeeId(1L);

        assertEquals(2, result.size());
        assertEquals("REC-001", result.get(0).getReceiptNumber());
    }

    @Test
    @DisplayName("getVisasByEmployeeId: returns empty list when employee has no visas")
    void getVisasByEmployeeId_emptyList() {
        when(visaRepository.findByEmployee_EmployeeId(99L)).thenReturn(Collections.emptyList());

        List<Visa> result = visaService.getVisasByEmployeeId(99L);

        assertTrue(result.isEmpty());
    }

    // ── getLatestVisaByEmployeeId ────────────────────────────────────────────────

    @Test
    @DisplayName("getLatestVisaByEmployeeId: returns most recent visa")
    void getLatestVisaByEmployeeId_returnsLatest() {
        when(visaRepository.findFirstByEmployee_EmployeeIdOrderByStartDateDesc(1L))
                .thenReturn(Optional.of(visa2)); // visa2 has later startDate

        Optional<Visa> result = visaService.getLatestVisaByEmployeeId(1L);

        assertTrue(result.isPresent());
        assertEquals("REC-002", result.get().getReceiptNumber());
        assertEquals(LocalDate.of(2024, 6, 1), result.get().getStartDate());
    }

    @Test
    @DisplayName("getLatestVisaByEmployeeId: returns empty when no visa exists")
    void getLatestVisaByEmployeeId_notFound() {
        when(visaRepository.findFirstByEmployee_EmployeeIdOrderByStartDateDesc(99L))
                .thenReturn(Optional.empty());

        Optional<Visa> result = visaService.getLatestVisaByEmployeeId(99L);

        assertFalse(result.isPresent());
    }

    // ── saveVisa ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveVisa: saves and returns the visa")
    void saveVisa_success() {
        // ensure create path (id == 0) so updateVisa isn't called
        visa1.setVisaId(0L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(visaRepository.save(visa1)).thenReturn(visa1);

        Visa result = visaService.saveVisa(visa1);

        assertNotNull(result);
        assertEquals(0L, result.getVisaId());
        verify(visaRepository).save(visa1);
    }

    @Test
    @DisplayName("saveVisa: visa with LCA is saved correctly")
    void saveVisa_withLca() {
        visa1.setVisaId(0L);
        LCA lca = new LCA();
        lca.setLcaId(201L);
        visa1.setLca(lca);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(lcaRepository.findById(201L)).thenReturn(Optional.of(lca));
        when(visaRepository.save(visa1)).thenReturn(visa1);

        Visa result = visaService.saveVisa(visa1);

        assertNotNull(result.getLca());
        assertEquals(201L, result.getLca().getLcaId());
    }

    @Test
    @DisplayName("saveVisa: visa without LCA (null) is saved correctly")
    void saveVisa_withoutLca() {
        visa1.setVisaId(0L);
        visa1.setLca(null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(visaRepository.save(visa1)).thenReturn(visa1);

        Visa result = visaService.saveVisa(visa1);

        assertNull(result.getLca());
    }

    // ── updateVisa ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateVisa: updates all fields and returns updated visa")
    void updateVisa_success() {
        Visa updatedDetails = makeVisa(101L, "L1A", "REC-UPDATED", employee,
                LocalDate.of(2025, 3, 1), LocalDate.of(2028, 3, 1));
        updatedDetails.setJobTitle("Senior Engineer");
        updatedDetails.setClient("Acme Corp");
        updatedDetails.setLcaWage(120000.0);
        updatedDetails.setStatus("active");

        when(visaRepository.findByVisaId(101L)).thenReturn(Optional.of(visa1));
        when(visaRepository.save(any(Visa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        Visa result = visaService.updateVisa(101L, updatedDetails);

        assertEquals("L1A", result.getVisaCategory());
        assertEquals("REC-UPDATED", result.getReceiptNumber());
        assertEquals("Senior Engineer", result.getJobTitle());
        assertEquals("Acme Corp", result.getClient());
        assertEquals(120000.0, result.getLcaWage());
        assertEquals("active", result.getStatus());
        verify(visaRepository).save(visa1);
    }

    @Test
    @DisplayName("updateVisa: throws ResourceNotFoundException when visa not found")
    void updateVisa_notFound() {
        when(visaRepository.findByVisaId(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visaService.updateVisa(999L, visa1));

        verify(visaRepository, never()).save(any());
    }

    // ── deleteVisa ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteVisa: deletes visa successfully when it exists")
    void deleteVisa_success() {
        when(visaRepository.existsById(101L)).thenReturn(true);
        doNothing().when(visaRepository).deleteById(101L);

        assertDoesNotThrow(() -> visaService.deleteVisa(101L));

        verify(visaRepository).deleteById(101L);
    }

    @Test
    @DisplayName("deleteVisa: throws ResourceNotFoundException when visa does not exist")
    void deleteVisa_notFound() {
        when(visaRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> visaService.deleteVisa(999L));

        verify(visaRepository, never()).deleteById(any());
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
