package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.controller.I9RecordController;
import com.employeehub.repository.I9RecordRepository;
import com.employeehub.service.I9RecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// I-9 is the most sensitive document category (SSN/A-numbers, work
// authorization) — every endpoint is locked to ADMIN/HR/IMMIGRATION, same
// access as the Employee Documents tabs granted this session. ACCOUNTING
// (which can reach plenty of other employee data) must still be rejected.
@ExtendWith(MockitoExtension.class)
class I9RecordControllerTest {

    @Mock
    private I9RecordService i9RecordService;
    @Mock
    private I9RecordRepository i9RecordRepository;

    @InjectMocks
    private I9RecordController controller;

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void accountingIsRejected() {
        CurrentUserContext.set("accounting@example.com", "ACCOUNTING");
        assertThrows(ResponseStatusException.class, () -> controller.getForEmployee(1L));
        assertThrows(ResponseStatusException.class, () -> controller.tasks());
    }

    @Test
    void hrImmigrationAndAdminAreAllowed() {
        when(i9RecordService.getForEmployee(1L)).thenReturn(
                new I9RecordService.EmployeeSummary(1L, "Compliant", null, null, java.util.List.of()));

        for (String role : new String[]{"HR", "IMMIGRATION", "ADMIN"}) {
            CurrentUserContext.set("user@example.com", role);
            assertDoesNotThrow(() -> controller.getForEmployee(1L));
        }
    }
}
