package com.employeehub;

import com.employeehub.i9.I9Config;
import com.employeehub.i9.I9Rules;
import com.employeehub.model.i9.I9Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every scenario from the I-9 rules spec's section 12 test table, each as
 * its own @Test (today = 2026-10-21, default config, unless the scenario
 * says otherwise). This is the authoritative Java half of the rules engine
 * — see also src/features/i9/i9Rules.test.ts on the frontend, which ports
 * the same table.
 */
class I9RulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @AfterEach
    void resetConfig() {
        I9Config.EVERIFY_ENROLLED = true;
    }

    // ───────────────────────────── fixture builders ─────────────────────────

    private I9Record record(I9Rules.SubType subtype, LocalDate validFrom) {
        I9Record r = new I9Record();
        r.setId(1L);
        r.setEmployeeId(100L);
        r.setRecordNo(1);
        r.setSubtype(subtype.name());
        r.setValidFrom(validFrom);
        return r;
    }

    private void listA(I9Record r, String code, String number, LocalDate expiry) {
        r.setDocOption(I9Rules.DocOption.LIST_A.name());
        r.getListA().setCode(code);
        r.getListA().setNumber(number);
        r.getListA().setExpiry(expiry);
    }

    private void listBC(I9Record r, String listBCode, String listCCode, LocalDate listCExpiry) {
        r.setDocOption(I9Rules.DocOption.LIST_BC.name());
        r.getListB().setCode(listBCode);
        r.getListB().setNumber("B-1");
        r.getListC().setCode(listCCode);
        r.getListC().setNumber("C-1");
        r.getListC().setExpiry(listCExpiry);
    }

    private void i94(I9Record r, String number, LocalDate admitUntil) {
        r.getI94().setNumber(number);
        r.getI94().setAdmitUntil(admitUntil);
    }

    private I9Rules.EmployeeContext ctx(LocalDate hireDate, LocalDate lastDay, String status, String visa) {
        return new I9Rules.EmployeeContext(100L, hireDate, lastDay, status, visa, null, null);
    }

    private I9Rules.EmployeeContext ctxWithPassport(LocalDate hireDate, String status, String visa,
                                                      String passportNumber, LocalDate passportExpiry) {
        return new I9Rules.EmployeeContext(100L, hireDate, null, status, visa, passportNumber, passportExpiry);
    }

    private void setExtension(I9Record r, I9Rules.ExtensionType type, String json) {
        r.setExtensionType(type.name());
        r.setExtensionDetailsJson(json);
    }

    // ───────────────────────────── scenarios ─────────────────────────────

    @Test
    void scenario1_citizenNewHireUsPassport() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 10, 19));
        r.setSection1Date(LocalDate.of(2026, 10, 19));
        r.setCompletedOn(LocalDate.of(2026, 10, 21));
        listA(r, I9Rules.A_US_PASSPORT, "P1", LocalDate.of(2031, 5, 1));
        r.getEverify().setResult("AUTHORIZED");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 10, 19), null, "Active", "Citizen");

        assertEquals(LocalDate.of(2026, 10, 22), I9Rules.section2Due(r, ctx, null));
        assertTrue(I9Rules.reverifyBy(r, List.of(r)).notRequired());
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
        assertTrue(I9Rules.openTasks(ctx, List.of(r), TODAY).isEmpty());
    }

    @Test
    void scenario2_newHireNeededThenOverdue() {
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 10, 16), null, "Active", "Citizen");
        I9Rules.EmployeeStatusResult onTime = I9Rules.employeeI9Status(ctx, List.of(), LocalDate.of(2026, 10, 21));
        assertEquals("New I-9 needed", onTime.status());
        assertEquals(LocalDate.of(2026, 10, 21), onTime.dueDate());

        I9Rules.EmployeeStatusResult late = I9Rules.employeeI9Status(ctx, List.of(), LocalDate.of(2026, 10, 22));
        assertEquals("New I-9 OVERDUE", late.status());

        List<I9Rules.Task> tasks = I9Rules.openTasks(ctx, List.of(), LocalDate.of(2026, 10, 21));
        assertEquals(1, tasks.size());
        assertEquals(I9Rules.Priority.HIGH, tasks.get(0).priority());
    }

    @Test
    void scenario3_citizenListBPlusUnrestrictedSsn() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listBC(r, "B_DL", I9Rules.C_SSN, null);
        r.getListC().setRestrictedSsn(false);
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "Citizen");

        assertTrue(I9Rules.reverifyBy(r, List.of(r)).notRequired());
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
    }

    @Test
    void scenario4_greenCardNeverReverifiesEvenAfterExpiry() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2020, 1, 1));
        r.setCompletedOn(LocalDate.of(2020, 1, 1));
        listA(r, I9Rules.A_GREEN_CARD, "GC1", LocalDate.of(2027, 1, 15));
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2020, 1, 1), null, "Active", "GC");

        assertTrue(I9Rules.reverifyBy(r, List.of(r)).notRequired());
        LocalDate afterExpiry = LocalDate.of(2027, 2, 1);
        assertTrue(I9Rules.openTasks(ctx, List.of(r), afterExpiry).stream()
                .noneMatch(t -> t.type() == I9Rules.TaskType.I9_REVERIFICATION));
    }

    @Test
    void scenario5_h1bFpiReverificationDue() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listA(r, I9Rules.A_FPI, "FP1", null);
        i94(r, "I94-1", LocalDate.of(2026, 12, 31));
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "H1B");

        I9Rules.ReverifyResult rv = I9Rules.reverifyBy(r, List.of(r));
        assertEquals(LocalDate.of(2026, 12, 31), rv.date());
        assertEquals("Reverification due", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());

        List<I9Rules.Task> tasks = I9Rules.openTasks(ctx, List.of(r), TODAY);
        I9Rules.Task reverifyTask = tasks.stream().filter(t -> t.type() == I9Rules.TaskType.I9_REVERIFICATION).findFirst().orElseThrow();
        assertEquals(I9Rules.Priority.MEDIUM, reverifyTask.priority());
        assertTrue(tasks.stream().noneMatch(t -> t.type() == I9Rules.TaskType.I94_EXPIRY));
    }

    @Test
    void scenario6_h1bFpiReverificationOverdueAndI94Expiry() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listA(r, I9Rules.A_FPI, "FP1", null);
        i94(r, "I94-1", LocalDate.of(2026, 10, 1));
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "H1B");

        assertEquals("Reverification OVERDUE", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
        List<I9Rules.Task> tasks = I9Rules.openTasks(ctx, List.of(r), TODAY);
        assertTrue(tasks.stream().anyMatch(t -> t.type() == I9Rules.TaskType.I9_REVERIFICATION && t.priority() == I9Rules.Priority.HIGH));
        assertTrue(tasks.stream().anyMatch(t -> t.type() == I9Rules.TaskType.I94_EXPIRY && t.priority() == I9Rules.Priority.HIGH));
    }

    @Test
    void scenario7_fpiMissingPassportNumberBlocksSave() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        listA(r, I9Rules.A_FPI, null, null);
        i94(r, "I94-1", LocalDate.of(2026, 12, 31));
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "H1B"); // no passport on file

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E11")));
    }

    @Test
    void scenario8_h1bFarFutureI94Compliant_passportExpiryTask() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listA(r, I9Rules.A_FPI, "FP1", null);
        i94(r, "I94-1", LocalDate.of(2028, 9, 30));
        I9Rules.EmployeeContext ctx = ctxWithPassport(LocalDate.of(2026, 1, 1), "Active", "H1B",
                "PP1", LocalDate.of(2027, 1, 15));

        assertEquals(LocalDate.of(2028, 9, 30), I9Rules.reverifyBy(r, List.of(r)).date());
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());

        List<I9Rules.Task> tasks = I9Rules.openTasks(ctx, List.of(r), TODAY);
        I9Rules.Task passportTask = tasks.stream().filter(t -> t.type() == I9Rules.TaskType.PASSPORT_EXPIRY).findFirst().orElseThrow();
        assertEquals(I9Rules.Priority.MEDIUM, passportTask.priority());
        assertTrue(tasks.stream().noneMatch(t -> t.type() == I9Rules.TaskType.I9_REVERIFICATION));
    }

    @Test
    void scenario9_initialOptEadReverificationDue() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listA(r, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 11, 30));
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "OPT");

        I9Rules.ReverifyResult rv = I9Rules.reverifyBy(r, List.of(r));
        assertEquals(LocalDate.of(2026, 11, 30), rv.date());
        assertEquals("This record", rv.source());
        I9Rules.Task task = I9Rules.openTasks(ctx, List.of(r), TODAY).stream()
                .filter(t -> t.type() == I9Rules.TaskType.I9_REVERIFICATION).findFirst().orElseThrow();
        assertEquals(I9Rules.Priority.MEDIUM, task.priority());
    }

    @Test
    void scenario10_eadNoExpiryBlockedThenMigratedShowsExpiryMissing() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        listA(r, I9Rules.A_EAD, "EAD1", null);
        I9Rules.EmployeeContext ctx = ctxWithPassport(LocalDate.of(2026, 1, 1), "Active", "OPT", "PP1",
                LocalDate.of(2030, 1, 1));
        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E14")));

        I9Record migrated = record(I9Rules.SubType.MIGRATED, LocalDate.of(2020, 1, 1));
        migrated.setCompletedOn(LocalDate.of(2020, 1, 1));
        listA(migrated, I9Rules.A_EAD, "EAD1", null);
        I9Rules.EmployeeContext migratedCtx = ctxWithPassport(LocalDate.of(2020, 1, 1), "Active", "OPT", "PP1",
                LocalDate.of(2030, 1, 1));

        assertEquals("Expiry date missing", I9Rules.employeeI9Status(migratedCtx, List.of(migrated), TODAY).status());
        I9Rules.Task task = I9Rules.openTasks(migratedCtx, List.of(migrated), TODAY).stream()
                .filter(t -> t.type() == I9Rules.TaskType.I9_DETAILS_MISSING).findFirst().orElseThrow();
        assertEquals(I9Rules.Priority.MEDIUM, task.priority());
    }

    @Test
    void scenario11_eadAutoExtensionCategoriesMatch() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        r.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(r, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 3, 1));
        setExtension(r, I9Rules.ExtensionType.EAD_AUTO_EXT,
                "{\"receivedDate\":\"2025-09-15\",\"categoryOnCard\":\"A11\",\"categoryOnReceipt\":\"A11\"}");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "OPT");

        I9Rules.ReverifyResult rv = I9Rules.reverifyBy(r, List.of(r));
        assertEquals(LocalDate.of(2027, 8, 23), rv.date());
        assertEquals("EAD EXT", I9Rules.suggestedNote(r));
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
    }

    @Test
    void scenario11b_eadAutoExtensionSpouseCategoryUsesEarlierI94() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        r.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(r, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 3, 1));
        i94(r, "I94-X", LocalDate.of(2027, 2, 15));
        setExtension(r, I9Rules.ExtensionType.EAD_AUTO_EXT,
                "{\"receivedDate\":\"2025-09-15\",\"categoryOnCard\":\"C26\",\"categoryOnReceipt\":\"C26\"}");

        assertEquals(LocalDate.of(2027, 2, 15), I9Rules.reverifyBy(r, List.of(r)).date());
    }

    @Test
    void scenario12_eadAutoExtensionAfterCutoffBlockedAndOverdueWithoutIt() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        r.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(r, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 3, 1));
        setExtension(r, I9Rules.ExtensionType.EAD_AUTO_EXT,
                "{\"receivedDate\":\"2025-11-15\",\"categoryOnCard\":\"A11\",\"categoryOnReceipt\":\"A11\"}");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "OPT");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E20")
                && f.message().contains("Renewals filed on or after 30-Oct-2025")));

        I9Rules.ReverifyResult rv = I9Rules.reverifyBy(r, List.of(r));
        assertEquals(LocalDate.of(2026, 3, 1), rv.date());
        assertEquals("Reverification OVERDUE", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
    }

    @Test
    void scenario13_stemOpt180DayExtension() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        r.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(r, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 9, 30));
        setExtension(r, I9Rules.ExtensionType.STEM_OPT_180_DAY,
                "{\"receiptDate\":\"2026-08-01\",\"i20Endorsed\":\"true\"}");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "OPT");

        assertEquals(LocalDate.of(2027, 3, 29), I9Rules.reverifyBy(r, List.of(r)).date());
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
    }

    @Test
    void scenario13b_stemOptReceiptAfterExpiryBlocked() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        listA(r, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 9, 30));
        setExtension(r, I9Rules.ExtensionType.STEM_OPT_180_DAY,
                "{\"receiptDate\":\"2026-10-05\",\"i20Endorsed\":\"true\"}");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "OPT");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E20")));
    }

    @Test
    void scenario14_capGapPendingThenApprovedOverdue() {
        I9Record pending = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        listA(pending, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 6, 15));
        setExtension(pending, I9Rules.ExtensionType.CAP_GAP,
                "{\"cosReceiptDate\":\"2026-04-01\",\"petitionStatus\":\"PENDING\",\"requestedStartFY\":\"2026\"}");
        assertEquals(LocalDate.of(2027, 4, 1), I9Rules.reverifyBy(pending, List.of(pending)).date());

        I9Record approved = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        approved.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(approved, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 6, 15));
        setExtension(approved, I9Rules.ExtensionType.CAP_GAP,
                "{\"cosReceiptDate\":\"2026-04-01\",\"petitionStatus\":\"APPROVED\",\"approvedStartDate\":\"2026-10-01\"}");
        I9Rules.ReverifyResult rv = I9Rules.reverifyBy(approved, List.of(approved));
        assertEquals(LocalDate.of(2026, 10, 1), rv.date());

        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "OPT");
        assertEquals("Reverification OVERDUE", I9Rules.employeeI9Status(ctx, List.of(approved), TODAY).status());
    }

    @Test
    void scenario15_h1b240DayExtension() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        r.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(r, I9Rules.A_FPI, "FP1", null);
        i94(r, "I94-1", LocalDate.of(2026, 10, 1));
        setExtension(r, I9Rules.ExtensionType.H1B_240_DAY,
                "{\"filingDate\":\"2026-08-15\",\"sameEmployer\":\"true\"}");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "H1B");

        I9Rules.ReverifyResult rv = I9Rules.reverifyBy(r, List.of(r));
        assertEquals(LocalDate.of(2027, 5, 29), rv.date());
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
        assertEquals("240-Day Ext. 08/15/2026", I9Rules.suggestedNote(r));
    }

    @Test
    void scenario16_h1b240DayExtensionFiledLateBlockedAndOverdueWithoutIt() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        r.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(r, I9Rules.A_FPI, "FP1", null);
        i94(r, "I94-1", LocalDate.of(2026, 10, 1));
        setExtension(r, I9Rules.ExtensionType.H1B_240_DAY,
                "{\"filingDate\":\"2026-10-05\",\"sameEmployer\":\"true\"}");
        I9Rules.EmployeeContext ctx = ctxWithPassport(LocalDate.of(2024, 1, 1), "Active", "H1B", "PP1",
                LocalDate.of(2030, 1, 1));

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E20")));

        assertEquals(LocalDate.of(2026, 10, 1), I9Rules.reverifyBy(r, List.of(r)).date());
        assertEquals("Reverification OVERDUE", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
    }

    @Test
    void scenario17_h1bPortabilityAc21NewHire() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 10, 19));
        r.setCompletedOn(LocalDate.of(2026, 10, 19));
        listA(r, I9Rules.A_FPI, "FP1", null);
        i94(r, "I94-1", LocalDate.of(2027, 3, 1));
        setExtension(r, I9Rules.ExtensionType.H1B_PORTABILITY_AC21, "{\"filingDate\":\"2026-10-10\"}");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 10, 19), null, "Active", "H1B");

        assertEquals(LocalDate.of(2026, 10, 22), I9Rules.section2Due(r, ctx, null));
        assertEquals(LocalDate.of(2027, 3, 1), I9Rules.reverifyBy(r, List.of(r)).date());
        assertEquals("AC-21 10/10/2026", I9Rules.suggestedNote(r));

        setExtension(r, I9Rules.ExtensionType.H1B_PORTABILITY_AC21,
                "{\"filingDate\":\"2026-10-10\",\"petitionStatus\":\"PENDING\"}");
        assertEquals(LocalDate.of(2027, 10, 27), I9Rules.reverifyBy(r, List.of(r)).date());
    }

    @Test
    void scenario18_h4EadChangesToH1bReverifyWithNewI94() {
        I9Record oldH4 = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2024, 1, 1));
        oldH4.setId(1L);
        oldH4.setCompletedOn(LocalDate.of(2024, 1, 1));
        listA(oldH4, I9Rules.A_EAD, "H4EAD1", LocalDate.of(2026, 1, 1));

        I9Record reverify = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 10, 1));
        reverify.setId(2L);
        reverify.setCompletedOn(LocalDate.of(2026, 10, 1));
        listA(reverify, I9Rules.A_FPI, "FP1", null);
        i94(reverify, "I94-1", LocalDate.of(2029, 9, 30));

        List<I9Record> all = List.of(oldH4, reverify);
        I9Record latest = I9Rules.latestRecord(all);
        assertEquals(2L, latest.getId());
        assertEquals(LocalDate.of(2029, 9, 30), I9Rules.reverifyBy(latest, all).date());

        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "H1B");
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, all, TODAY).status());
    }

    @Test
    void scenario19_l2sSpouseListCDhsEad() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listBC(r, "B_DL", I9Rules.C_DHS_EAD, LocalDate.of(2027, 6, 30));
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "L1");

        assertEquals(LocalDate.of(2027, 6, 30), I9Rules.reverifyBy(r, List.of(r)).date());
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, List.of(r), TODAY).status());
    }

    @Test
    void scenario20_newHireListCOnlyBlocked() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setDocOption(I9Rules.DocOption.LIST_C_ONLY.name());
        r.getListC().setCode(I9Rules.C_SSN);
        r.getListC().setNumber("SSN1");
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "Citizen");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E8")));
    }

    @Test
    void scenario21_reverifyWithListBBlocked() {
        I9Record r = record(I9Rules.SubType.REVERIFY, LocalDate.of(2026, 1, 1));
        listBC(r, "B_DL", I9Rules.C_SSN, null);
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2024, 1, 1), null, "Active", "H1B");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E9")));
    }

    @Test
    void scenario22_rehireSuppBWithinThreeYears() {
        I9Record r = record(I9Rules.SubType.REHIRE_SUPP_B, LocalDate.of(2026, 11, 2));
        r.setOriginalI9Date(LocalDate.of(2024, 5, 1));
        r.setPriorAuthExpired(false);
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 11, 2), null, "Active", "Citizen");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.isValid());
        assertEquals(LocalDate.of(2026, 11, 2), I9Rules.section2Due(r, ctx, null));
    }

    @Test
    void scenario23_rehireSuppBOverThreeYearsBlocked() {
        I9Record r = record(I9Rules.SubType.REHIRE_SUPP_B, LocalDate.of(2026, 11, 2));
        r.setOriginalI9Date(LocalDate.of(2022, 3, 1));
        r.setPriorAuthExpired(false);
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 11, 2), null, "Active", "Citizen");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E19")));

        I9Record asNewHire = record(I9Rules.SubType.REHIRE_NEW, LocalDate.of(2026, 11, 2));
        assertEquals(LocalDate.of(2026, 11, 5), I9Rules.section2Due(asNewHire, ctx, null));
    }

    @Test
    void scenario24_exitedRetentionEndedThenDestroyedClears() {
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2020, 1, 6), LocalDate.of(2021, 2, 15), "Inactive", "Citizen");
        assertEquals(LocalDate.of(2023, 1, 6), I9Rules.retainUntil(ctx.hireDate(), ctx.lastDay()));

        List<I9Rules.Task> beforeDestroy = I9Rules.openTasks(ctx, List.of(), TODAY);
        assertTrue(beforeDestroy.stream().anyMatch(t -> t.type() == I9Rules.TaskType.I9_RETENTION_ENDED
                && t.priority() == I9Rules.Priority.LOW));

        I9Record destroyed = record(I9Rules.SubType.DESTROYED, LocalDate.of(2026, 10, 21));
        I9Rules.ValidationResult result = I9Rules.validate(destroyed, ctx, List.of());
        assertTrue(result.isValid());
        assertEquals("Destroyed", I9Rules.recordStatus(destroyed));

        List<I9Rules.Task> afterDestroy = I9Rules.openTasks(ctx, List.of(destroyed), TODAY);
        assertTrue(afterDestroy.stream().noneMatch(t -> t.type() == I9Rules.TaskType.I9_RETENTION_ENDED));
    }

    @Test
    void scenario24b_exitedRetentionNotEndedDestroyBlocked() {
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2018, 3, 1), LocalDate.of(2026, 6, 30), "Inactive", "Citizen");
        assertEquals(LocalDate.of(2027, 6, 30), I9Rules.retainUntil(ctx.hireDate(), ctx.lastDay()));

        I9Record destroyed = record(I9Rules.SubType.DESTROYED, TODAY);
        I9Rules.ValidationResult result = I9Rules.validate(destroyed, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E24")));
    }

    @Test
    void scenario25_docUpdateDoesNotBecomeLatestOrAffectCount() {
        I9Record newHire = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        newHire.setId(1L);
        newHire.setCompletedOn(LocalDate.of(2026, 1, 1));
        listA(newHire, I9Rules.A_US_PASSPORT, "P1", LocalDate.of(2030, 1, 1));

        I9Record docUpdate = record(I9Rules.SubType.DOC_UPDATE, LocalDate.of(2026, 10, 15));
        docUpdate.setId(2L);

        List<I9Record> all = List.of(newHire, docUpdate);
        assertEquals(1L, I9Rules.latestRecord(all).getId());
        assertEquals(1, I9Rules.i9Count(all));

        // The DOC_UPDATE's new passport feeds the separate Passport table
        // (see I9RecordService.buildContext), not I9Rules directly — this
        // asserts the resulting task-free state once that table reflects it.
        I9Rules.EmployeeContext ctx = ctxWithPassport(LocalDate.of(2026, 1, 1), "Active", "Citizen", "P1",
                LocalDate.of(2036, 5, 1));
        assertTrue(I9Rules.openTasks(ctx, all, TODAY).stream().noneMatch(t -> t.type() == I9Rules.TaskType.PASSPORT_EXPIRY));
    }

    @Test
    void scenario26_expiredUsPassportExaminedBlocked() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 10, 19));
        r.setCompletedOn(LocalDate.of(2026, 10, 19));
        listA(r, I9Rules.A_US_PASSPORT, "P1", LocalDate.of(2026, 1, 1));
        I9Rules.EmployeeContext ctx = ctxWithPassport(LocalDate.of(2026, 10, 19), "Active", "Citizen", "P1",
                LocalDate.of(2026, 1, 1));

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E16")));
    }

    @Test
    void scenario27_receiptForLostEadCreatesFollowUpTask() {
        LocalDate examDate = LocalDate.of(2026, 10, 19);
        assertEquals(LocalDate.of(2027, 1, 17),
                I9Rules.receiptValidUntil(I9Rules.ReceiptType.REPLACEMENT, examDate, new I9Record()));

        I9Record r = record(I9Rules.SubType.NEW_HIRE, examDate);
        r.getReceipt().setType(I9Rules.ReceiptType.REPLACEMENT.name());
        r.getReceipt().setValidUntil(LocalDate.of(2027, 1, 17));
        I9Rules.EmployeeContext ctx = ctx(examDate, null, "Active", "OPT");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.isValid(), "receipt should bypass Document Option requirements: " + result.errors());

        I9Rules.Task task = I9Rules.openTasks(ctx, List.of(r), TODAY).stream()
                .filter(t -> t.type() == I9Rules.TaskType.RECEIPT_FOLLOWUP).findFirst().orElseThrow();
        assertEquals(I9Rules.Priority.HIGH, task.priority());
        assertEquals(LocalDate.of(2027, 1, 17), task.due());
    }

    @Test
    void scenario28_usPassportButH1bVisaWarningOnly() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listA(r, I9Rules.A_US_PASSPORT, "P1", LocalDate.of(2030, 1, 1));
        I9Rules.EmployeeContext ctx = ctxWithPassport(LocalDate.of(2026, 1, 1), "Active", "H1B", "P1",
                LocalDate.of(2030, 1, 1));

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.isValid());
        assertTrue(result.warnings().stream().anyMatch(f -> f.code().equals("W1")));
    }

    @Test
    void scenario29_newHireUnder3DaysSection2DueSameDay() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 10, 19));
        r.setUnder3Days(true);
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 10, 19), null, "Active", "Citizen");

        assertEquals(LocalDate.of(2026, 10, 19), I9Rules.section2Due(r, ctx, null));
    }

    @Test
    void scenario30a_remoteExamWithoutEverifyEnrolledBlocked() {
        I9Config.EVERIFY_ENROLLED = false;
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setRemoteExam(true);
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "Citizen");

        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.errors().stream().anyMatch(f -> f.code().equals("E21")));
    }

    @Test
    void scenario31_nameChangeDoesNotBecomeLatest() {
        I9Record scenario5Record = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        scenario5Record.setId(1L);
        listA(scenario5Record, I9Rules.A_FPI, "FP1", null);
        i94(scenario5Record, "I94-1", LocalDate.of(2026, 12, 31));

        I9Record nameChange = record(I9Rules.SubType.NAME_CHANGE, LocalDate.of(2026, 10, 25));
        nameChange.setId(2L);

        List<I9Record> all = List.of(scenario5Record, nameChange);
        assertEquals(1L, I9Rules.latestRecord(all).getId());
    }

    @Test
    void scenario33_activeNoHireDateNoRecords() {
        I9Rules.EmployeeContext ctx = ctx(null, null, "Active", "H1B");
        I9Rules.EmployeeStatusResult status = I9Rules.employeeI9Status(ctx, List.of(), TODAY);
        assertEquals("No I-9 on file", status.status());

        I9Rules.Task task = I9Rules.openTasks(ctx, List.of(), TODAY).stream()
                .filter(t -> t.type() == I9Rules.TaskType.NEW_I9_NEEDED).findFirst().orElseThrow();
        assertEquals(I9Rules.Priority.HIGH, task.priority());
    }

    @Test
    void scenario34_correctionAppliesOntoOriginalKeepingHistory() {
        I9Record original = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        original.setId(9L);
        original.setCompletedOn(LocalDate.of(2026, 1, 1));
        listA(original, I9Rules.A_EAD, "EAD1", LocalDate.of(2026, 11, 30));

        I9Record correction = record(I9Rules.SubType.CORRECTION, LocalDate.of(2026, 10, 21));
        correction.setId(10L);
        correction.setCorrectsRecordId(9L);
        correction.setCorrectionReason("EAD expiry was mistyped");
        correction.getListA().setExpiry(LocalDate.of(2027, 4, 30));

        List<I9Record> all = List.of(original, correction);
        I9Record effective = I9Rules.effectiveRecord(original, all);
        assertEquals(LocalDate.of(2027, 4, 30), effective.getListA().getExpiry());
        assertEquals(I9Rules.A_EAD, effective.getListA().getCode());
        // History preserved — the original row itself is untouched.
        assertEquals(LocalDate.of(2026, 11, 30), original.getListA().getExpiry());

        assertEquals(LocalDate.of(2027, 4, 30), I9Rules.reverifyBy(effective, all).date());
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "OPT");
        assertEquals("Compliant", I9Rules.employeeI9Status(ctx, all, TODAY).status());
    }

    @Test
    void scenario35_suggestedDocumentsNeverRestrictTheChoice() {
        I9Record r = record(I9Rules.SubType.NEW_HIRE, LocalDate.of(2026, 1, 1));
        r.setCompletedOn(LocalDate.of(2026, 1, 1));
        listBC(r, "B_DL", I9Rules.C_SSN, null);
        r.getListC().setRestrictedSsn(false);
        I9Rules.EmployeeContext ctx = ctx(LocalDate.of(2026, 1, 1), null, "Active", "H1B");

        // H1B's suggested document is "Foreign Passport + I-94" (List A),
        // but the employee presented List B + List C instead — allowed,
        // no warning about the choice itself.
        I9Rules.ValidationResult result = I9Rules.validate(r, ctx, List.of());
        assertTrue(result.isValid());
        assertTrue(result.warnings().stream().noneMatch(f -> f.message().toLowerCase().contains("list b")
                || f.message().toLowerCase().contains("suggested")));
    }
}
