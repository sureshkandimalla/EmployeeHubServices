package com.employeehub.controller;

import com.employeehub.config.CurrentUserContext;
import com.employeehub.exception.ResourceNotFoundException;
import com.employeehub.model.Expense;
import com.employeehub.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/expense")
public class ExpenseController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ExpenseController.class);

    @Autowired
    private ExpenseRepository expenseRepository;

    @GetMapping("/getAllExpenses")
    public ResponseEntity<List<Expense>> getAllExpenses() {
        return ResponseEntity.ok(expenseRepository.findAll());
    }

    @GetMapping("/expenses/{id}")
    public ResponseEntity<Expense> getExpenseById(@PathVariable Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not exist with id: " + id));
        return ResponseEntity.ok(expense);
    }

    @GetMapping("/getExpensesForEmployee")
    public ResponseEntity<List<Expense>> getExpensesForEmployee(@RequestParam(required = true) Long employeeId) {
        return ResponseEntity.ok(expenseRepository.findByEmployeeId(employeeId));
    }

    @GetMapping("/expensesCountByStatus")
    public List<Map<String, String>> getExpensesCountByStatus() {
        return expenseRepository.getExpenseCountByStatus();
    }

    @PostMapping("/addExpense")
    public ResponseEntity<Expense> createExpense(@RequestBody Expense expense) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
        if (expense.getStatus() == null || expense.getStatus().isEmpty()) {
            expense.setStatus("Pending");
        }
        Expense savedExpense = expenseRepository.save(expense);
        return new ResponseEntity<>(savedExpense, HttpStatus.CREATED);
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<Expense> updateExpense(@PathVariable Long id, @RequestBody Expense expenseDetails) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
        expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not exist with id: " + id));
        expenseDetails.setExpenseId(id);
        Expense updatedExpense = expenseRepository.save(expenseDetails);
        return ResponseEntity.ok(updatedExpense);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        CurrentUserContext.requireRole("ADMIN", "ACCOUNTING");
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not exist with id: " + id));
        expenseRepository.delete(expense);
        return ResponseEntity.noContent().build();
    }
}
