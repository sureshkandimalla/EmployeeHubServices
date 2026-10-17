package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.repository.AdjustmentRepository;
import com.employeehub.controller.AdjustmentController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
public class AdjustmentControllerTest {

    @Mock
    private AdjustmentRepository adjustmentRepository;

    @InjectMocks
    private AdjustmentController adjustmentController;

    private MockMvc mockMvc;

    @BeforeEach
    public void setUpRole() {
        CurrentUserContext.set("test@example.com", "ACCOUNTING");
    }

    @AfterEach
    public void clearRole() {
        CurrentUserContext.clear();
    }

    @Test
    public void testFindAdjustmentsByEmployeeId() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(adjustmentController).build();

        // Row shape matches domain.Adjustment(Object[]): adjustmentId, lastUpdated,
        // adjustmentDate, adjustmentType, amount, notes, fromId, toId, fromName, toName.
        // toId == the queried employeeId (1) on both rows, so the controller negates
        // each amount (money owed TO this employee is a positive expense from their view).
        Date today = Date.valueOf("2026-01-01");
        Object[] row1 = {BigInteger.valueOf(1), today, today, "Bonus", 100.0, "note1",
                BigInteger.valueOf(2), BigInteger.valueOf(1), "From Name", "To Name"};
        Object[] row2 = {BigInteger.valueOf(2), today, today, "Bonus", 200.0, "note2",
                BigInteger.valueOf(2), BigInteger.valueOf(1), "From Name", "To Name"};

        List<Object[]> adjustments = Arrays.asList(row1, row2);

        when(adjustmentRepository.findAdjustmentsByEmployeeId(1L)).thenReturn(adjustments);

        mockMvc.perform(get("/api/v1/adjustment/findAdjustmentsByEmployeeId")
                .param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount", is(-100.0)))
                .andExpect(jsonPath("$[1].amount", is(-200.0)));
    }
}