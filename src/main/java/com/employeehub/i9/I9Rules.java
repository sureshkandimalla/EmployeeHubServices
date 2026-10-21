package com.employeehub.i9;

import com.employeehub.model.i9.I9Record;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Pure, framework-free Form I-9 rules engine — the Java twin of the
// frontend's src/features/i9/i9Rules.ts. Both implement the same rules
// (USCIS M-274 Handbook for Employers, as of October 2026, including the
// 30-Oct-2025 end of automatic EAD extensions for new renewal filings) and
// are kept in sync by porting the same 35 scenarios into both test suites
// — see I9RulesTest.java and i9Rules.test.jsx. This copy is authoritative:
// I9RecordService always re-validates here before saving, never trusting
// whatever the client computed.
//
// `today` is always passed in explicitly, never read with LocalDate.now()
// inside this class, so every method stays deterministic and testable.
//
// Known gap vs. the spec: there is no structured I-94/EAD data anywhere
// else in this codebase (the Visa/case-petition model has no admit-until
// or EAD-expiry fields), so the "migrated record" fallback in reverifyBy
// reads the employee's own other I9Records instead of a visa-case table.
public final class I9Rules {

    private I9Rules() {
    }

    // ───────────────────────────── Enums (section 2, 3.3, 5.3, 5.5, 8) ─────

    public enum SubType {
        NEW_HIRE, REVERIFY, REHIRE_SUPP_B, REHIRE_NEW, NAME_CHANGE,
        CORRECTION, DOC_UPDATE, MIGRATED, DESTROYED
    }

    public enum Attestation {
        CITIZEN, NONCITIZEN_NATIONAL, LPR, NONCITIZEN_AUTHORIZED
    }

    public enum DocOption {
        LIST_A, LIST_BC, LIST_C_ONLY
    }

    // List A codes (section 3.3).
    public static final String A_US_PASSPORT = "A_US_PASSPORT";
    public static final String A_GREEN_CARD = "A_GREEN_CARD";
    public static final String A_TEMP_I551 = "A_TEMP_I551";
    public static final String A_EAD = "A_EAD";
    public static final String A_FPI = "A_FPI";
    public static final String A_FSM_RMI = "A_FSM_RMI";
    public static final String A_OTHER = "A_OTHER";

    // List C codes that matter to the rules (the rest of List C never
    // expires and is never reverified).
    public static final String C_SSN = "C_SSN";
    public static final String C_DHS_EAD = "C_DHS_EAD";

    public enum ExtensionType {
        EAD_AUTO_EXT, H1B_240_DAY, H1B_PORTABILITY_AC21, STEM_OPT_180_DAY,
        CAP_GAP, TPS_FEDERAL_REGISTER, GREEN_CARD_EXTENSION
    }

    public enum ReceiptType {
        REPLACEMENT, I94_TEMP_I551, I94_REFUGEE
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }

    public enum TaskType {
        NEW_I9_NEEDED, I9_INCOMPLETE, I9_REVERIFICATION, I9_DETAILS_MISSING,
        EVERIFY_PENDING, RECEIPT_FOLLOWUP, PASSPORT_EXPIRY, I94_EXPIRY,
        EXIT_DETAILS_MISSING, I9_RETENTION_ENDED
    }

    // ───────────────────────────── Result types ─────────────────────────────

    public record Finding(String code, String message) {
    }

    public record ValidationResult(List<Finding> errors, List<Finding> warnings) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public record ReverifyResult(LocalDate date, String source, boolean notRequired) {
        public static ReverifyResult notRequiredResult() {
            return new ReverifyResult(null, null, true);
        }
    }

    public record EmployeeStatusResult(String status, LocalDate dueDate) {
    }

    public record Task(Long employeeId, TaskType type, String label, Priority priority,
                        LocalDate due, String whereToFix) {
    }

    // Everything about the employee I9Rules needs that doesn't live on an
    // I9Record itself. passportNumber/passportExpiry come from the
    // existing Passport table (see PassportRepository), not duplicated
    // into I9Record (per the project's decision to reuse that table).
    public record EmployeeContext(Long employeeId, LocalDate hireDate, LocalDate lastDay,
                                   String employmentStatus, String visaStatus,
                                   String passportNumber, LocalDate passportExpiry) {

        // This app's working-status values (config.js workingStatusList)
        // don't include the spec's literal "Terminated"/"Withdrawn" — the
        // closest equivalents are Inactive and Archived.
        public boolean hasExited() {
            return "Inactive".equalsIgnoreCase(employmentStatus) || "Archived".equalsIgnoreCase(employmentStatus);
        }
    }

    // ───────────────────────────── Business days (5.1) ───────────────────────

    public static LocalDate addBusinessDays(LocalDate start, int days, Set<LocalDate> holidays) {
        LocalDate date = start;
        int remaining = days;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (isBusinessDay(date, holidays)) remaining--;
        }
        return date;
    }

    private static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !holidays.contains(date);
    }

    // ───────────────────────────── 6. Which record is "latest" ──────────────

    private static final Set<SubType> EXCLUDED_FROM_LATEST = Set.of(SubType.DOC_UPDATE, SubType.NAME_CHANGE,
            SubType.CORRECTION);

    public static I9Record latestRecord(List<I9Record> records) {
        return records.stream()
                .filter(r -> !EXCLUDED_FROM_LATEST.contains(SubType.valueOf(r.getSubtype())))
                .max(Comparator.comparing(I9Record::getValidFrom, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(I9Record::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    // A CORRECTION applies its (non-null) field values onto the record it
    // corrects, for display/rules purposes only — the original row's own
    // columns are never overwritten, so its history stays intact (section
    // 9's audit-trail guardrail). Returns `original` unchanged if no
    // correction targets it.
    public static I9Record effectiveRecord(I9Record original, List<I9Record> allRecordsForEmployee) {
        if (original == null) return null;
        I9Record correction = allRecordsForEmployee.stream()
                .filter(r -> SubType.CORRECTION.name().equals(r.getSubtype()))
                .filter(r -> original.getId() != null && original.getId().equals(r.getCorrectsRecordId()))
                .max(Comparator.comparing(I9Record::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (correction == null) return original;

        I9Record merged = new I9Record();
        merged.setId(original.getId());
        merged.setEmployeeId(original.getEmployeeId());
        merged.setRecordNo(original.getRecordNo());
        merged.setSubtype(original.getSubtype());
        merged.setValidFrom(original.getValidFrom());
        merged.setSection1Date(original.getSection1Date());
        merged.setAttestation(original.getAttestation());
        merged.setSection1AuthExpiry(original.getSection1AuthExpiry());
        merged.setUnder3Days(original.getUnder3Days());
        merged.setCompletedOn(original.getCompletedOn());
        merged.setDocOption(coalesce(correction.getDocOption(), original.getDocOption()));
        merged.setListA(mergeListA(original, correction));
        merged.setI94(mergeI94(original, correction));
        merged.setListB(mergeListB(original, correction));
        merged.setListC(mergeListC(original, correction));
        merged.setReceipt(original.getReceipt());
        merged.setExtensionType(coalesce(correction.getExtensionType(), original.getExtensionType()));
        merged.setExtensionDetailsJson(coalesce(correction.getExtensionDetailsJson(), original.getExtensionDetailsJson()));
        merged.setPriorAuthExpired(original.getPriorAuthExpired());
        merged.setOriginalI9Date(original.getOriginalI9Date());
        merged.setEverify(original.getEverify());
        merged.setCreatedAt(original.getCreatedAt());
        return merged;
    }

    private static <T> T coalesce(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static com.employeehub.model.i9.ListADetails mergeListA(I9Record original, I9Record correction) {
        var o = original.getListA();
        var c = correction.getListA();
        var merged = new com.employeehub.model.i9.ListADetails();
        merged.setCode(coalesce(c.getCode(), o.getCode()));
        merged.setNumber(coalesce(c.getNumber(), o.getNumber()));
        merged.setExpiry(coalesce(c.getExpiry(), o.getExpiry()));
        merged.setNoExpiry(coalesce(c.getNoExpiry(), o.getNoExpiry()));
        return merged;
    }

    private static com.employeehub.model.i9.I94Details mergeI94(I9Record original, I9Record correction) {
        var o = original.getI94();
        var c = correction.getI94();
        var merged = new com.employeehub.model.i9.I94Details();
        merged.setNumber(coalesce(c.getNumber(), o.getNumber()));
        merged.setAdmitUntil(coalesce(c.getAdmitUntil(), o.getAdmitUntil()));
        merged.setDs(coalesce(c.getDs(), o.getDs()));
        merged.setAuthEndDate(coalesce(c.getAuthEndDate(), o.getAuthEndDate()));
        return merged;
    }

    private static com.employeehub.model.i9.ListBDetails mergeListB(I9Record original, I9Record correction) {
        var o = original.getListB();
        var c = correction.getListB();
        var merged = new com.employeehub.model.i9.ListBDetails();
        merged.setCode(coalesce(c.getCode(), o.getCode()));
        merged.setNumber(coalesce(c.getNumber(), o.getNumber()));
        merged.setExpiry(coalesce(c.getExpiry(), o.getExpiry()));
        return merged;
    }

    private static com.employeehub.model.i9.ListCDetails mergeListC(I9Record original, I9Record correction) {
        var o = original.getListC();
        var c = correction.getListC();
        var merged = new com.employeehub.model.i9.ListCDetails();
        merged.setCode(coalesce(c.getCode(), o.getCode()));
        merged.setNumber(coalesce(c.getNumber(), o.getNumber()));
        merged.setExpiry(coalesce(c.getExpiry(), o.getExpiry()));
        merged.setRestrictedSsn(coalesce(c.getRestrictedSsn(), o.getRestrictedSsn()));
        return merged;
    }

    // ───────────────────────────── 5.1 Section 2 due date ─────────────────────

    public static LocalDate section2Due(I9Record record, EmployeeContext ctx, I9Record priorRecordBeingReplaced) {
        SubType sub = SubType.valueOf(record.getSubtype());
        return switch (sub) {
            case NEW_HIRE, REHIRE_NEW -> Boolean.TRUE.equals(record.getUnder3Days())
                    ? ctx.hireDate()
                    : addBusinessDays(ctx.hireDate(), I9Config.SECTION2_BUSINESS_DAYS, I9Config.HOLIDAYS);
            case REHIRE_SUPP_B -> record.getValidFrom();
            case REVERIFY -> priorRecordBeingReplaced != null
                    ? reverifyBy(priorRecordBeingReplaced, List.of()).date()
                    : null;
            default -> null;
        };
    }

    // ───────────────────────────── 5.2 E-Verify due date ───────────────────────

    public static LocalDate everifyDue(I9Record record, EmployeeContext ctx) {
        SubType sub = SubType.valueOf(record.getSubtype());
        if ((sub == SubType.NEW_HIRE || sub == SubType.REHIRE_NEW) && I9Config.EVERIFY_ENROLLED) {
            return addBusinessDays(ctx.hireDate(), I9Config.EVERIFY_BUSINESS_DAYS, I9Config.HOLIDAYS);
        }
        return null; // REVERIFY and everything else: never.
    }

    // ───────────────────────────── 5.3 Reverify by ───────────────────────────

    // documents that are never reverified (step 1)
    private static boolean neverReverify(I9Record r) {
        String listACode = r.getListA() != null ? r.getListA().getCode() : null;
        if (A_US_PASSPORT.equals(listACode) || A_GREEN_CARD.equals(listACode)) return true;
        boolean usedListBC = DocOption.LIST_BC.name().equals(r.getDocOption());
        if (usedListBC) {
            String listCCode = r.getListC() != null ? r.getListC().getCode() : null;
            // Only C_DHS_EAD (List C #7) ever expires; every other List C
            // document (SSN card, birth certificate, ...) does not.
            return !C_DHS_EAD.equals(listCCode);
        }
        return false;
    }

    public static ReverifyResult reverifyBy(I9Record record, List<I9Record> allRecordsForEmployee) {
        if (record == null) return ReverifyResult.notRequiredResult();
        if (neverReverify(record)) return ReverifyResult.notRequiredResult();

        LocalDate base = null;
        String source = "This record";
        String listACode = record.getListA() != null ? record.getListA().getCode() : null;

        if (A_EAD.equals(listACode)) {
            base = record.getListA().getExpiry();
        } else if (A_FPI.equals(listACode) || A_FSM_RMI.equals(listACode)) {
            var i94 = record.getI94();
            base = Boolean.TRUE.equals(i94.getDs()) ? i94.getAuthEndDate() : i94.getAdmitUntil();
        } else if (A_TEMP_I551.equals(listACode) || A_OTHER.equals(listACode)) {
            base = record.getListA().getExpiry();
        } else if (C_DHS_EAD.equals(record.getListC() != null ? record.getListC().getCode() : null)) {
            base = record.getListC().getExpiry();
        }

        // Section 1: noncitizen authorized to work until a date — the
        // earlier of that date and the document date.
        if (Attestation.NONCITIZEN_AUTHORIZED.name().equals(record.getAttestation())
                && record.getSection1AuthExpiry() != null) {
            base = base == null ? record.getSection1AuthExpiry()
                    : (record.getSection1AuthExpiry().isBefore(base) ? record.getSection1AuthExpiry() : base);
            if (source.equals("This record") && base.equals(record.getSection1AuthExpiry())) {
                source = "Section 1 date";
            }
        }

        // Fallback for migrated/old records missing their own date: use
        // the latest EAD/I-94 data found on any of the employee's other
        // I9Records (see class javadoc — no separate visa-case table has
        // this data).
        if (base == null) {
            if (A_EAD.equals(listACode)) {
                LocalDate fallback = latestFieldAcrossRecords(allRecordsForEmployee,
                        r -> A_EAD.equals(r.getListA().getCode()) ? r.getListA().getExpiry() : null);
                if (fallback != null) {
                    base = fallback;
                    source = "Latest EAD on file";
                }
            } else if ((A_FPI.equals(listACode) || A_FSM_RMI.equals(listACode))
                    && record.getI94().getAdmitUntil() == null) {
                LocalDate fallback = latestFieldAcrossRecords(allRecordsForEmployee,
                        r -> r.getI94() != null ? r.getI94().getAdmitUntil() : null);
                if (fallback != null) {
                    base = fallback;
                    source = "Latest I-94 on file";
                }
            }
        }

        // Step 3: apply a chosen extension.
        if (record.getExtensionType() != null) {
            I9ExtensionCalculator.Result ext = I9ExtensionCalculator.apply(record, base);
            if (ext != null) {
                base = ext.reverifyBy();
                source = "Extension: " + record.getExtensionType();
            }
        }

        if (base == null) return new ReverifyResult(null, null, false);
        return new ReverifyResult(base, source, false);
    }

    private interface FieldExtractor {
        LocalDate extract(I9Record r);
    }

    private static LocalDate latestFieldAcrossRecords(List<I9Record> records, FieldExtractor extractor) {
        return records.stream()
                .map(extractor::extract)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    // ───────────────────────────── 5.4 Suggested notes ───────────────────────

    public static String suggestedNote(I9Record record) {
        if (record.getExtensionType() == null) return null;
        ExtensionType type = ExtensionType.valueOf(record.getExtensionType());
        return I9ExtensionCalculator.suggestedNote(type, record);
    }

    // ───────────────────────────── 5.5 Receipts ──────────────────────────────

    public static LocalDate receiptValidUntil(ReceiptType type, LocalDate examDate, I9Record record) {
        return switch (type) {
            case REPLACEMENT -> examDate.plusDays(I9Config.RECEIPT_REPLACEMENT_DAYS);
            case I94_TEMP_I551 -> record.getI94() != null && record.getI94().getAdmitUntil() != null
                    ? record.getI94().getAdmitUntil()
                    : examDate.plusDays(I9Config.TEMP_I551_NO_EXPIRY_DAYS);
            case I94_REFUGEE -> examDate.plusDays(I9Config.REFUGEE_RECEIPT_DAYS);
        };
    }

    // ───────────────────────────── 5.6 Retention date ────────────────────────

    public static LocalDate retainUntil(LocalDate hireDate, LocalDate lastDay) {
        if (hireDate == null || lastDay == null) return null;
        LocalDate afterHire = hireDate.plusYears(I9Config.RETENTION_YEARS_AFTER_HIRE);
        LocalDate afterExit = lastDay.plusYears(I9Config.RETENTION_YEARS_AFTER_EXIT);
        return afterHire.isAfter(afterExit) ? afterHire : afterExit;
    }

    // ───────────────────────────── 7. Employee I-9 status ────────────────────

    public static EmployeeStatusResult employeeI9Status(EmployeeContext ctx, List<I9Record> records,
                                                          LocalDate today) {
        // 1. Exited employees: retention only, no action status.
        if (ctx.hasExited()) return new EmployeeStatusResult(null, null);

        I9Record latest = latestRecord(records);

        // 2/3. No records, or a rehire (hire date after the latest record).
        boolean isRehire = latest != null && ctx.hireDate() != null
                && latest.getValidFrom() != null && ctx.hireDate().isAfter(latest.getValidFrom());
        if (latest == null || isRehire) {
            if (ctx.hireDate() == null) return new EmployeeStatusResult("No I-9 on file", null);
            LocalDate due = addBusinessDays(ctx.hireDate(), I9Config.SECTION2_BUSINESS_DAYS, I9Config.HOLIDAYS);
            boolean overdue = today.isAfter(due);
            String label = isRehire ? (overdue ? "Rehire I-9 OVERDUE" : "Rehire I-9 needed")
                    : (overdue ? "New I-9 OVERDUE" : "New I-9 needed");
            return new EmployeeStatusResult(label, due);
        }

        I9Record effective = effectiveRecord(latest, records);
        Set<String> completeStatuses = Set.of("Completed", "Reverified", "N/A");

        // 4. Latest record not complete.
        if (effective.getCompletedOn() == null && !completeStatuses.contains(recordStatus(effective))) {
            LocalDate due = section2Due(effective, ctx, null);
            ReverifyResult rv = reverifyBy(effective, records);
            boolean workAuthExpired = !rv.notRequired() && rv.date() != null && rv.date().isBefore(today);
            if ((due != null && today.isAfter(due)) || workAuthExpired) {
                return new EmployeeStatusResult("I-9 OVERDUE", due);
            }
            return new EmployeeStatusResult("I-9 in progress", due);
        }

        // 5. Reverification.
        ReverifyResult rv = reverifyBy(effective, records);
        if (!rv.notRequired()) {
            if (rv.date() == null) return new EmployeeStatusResult("Expiry date missing", null);
            long daysLeft = ChronoUnit.DAYS.between(today, rv.date());
            if (rv.date().isBefore(today)) return new EmployeeStatusResult("Reverification OVERDUE", rv.date());
            if (daysLeft <= I9Config.I9_REVERIFY_ALERT_DAYS) {
                return new EmployeeStatusResult("Reverification due", rv.date());
            }
            return new EmployeeStatusResult("Compliant", rv.date());
        }

        // 6. Reverify result blank (not required) and visa isn't Citizen/GC.
        boolean citizenOrGc = "Citizen".equalsIgnoreCase(ctx.visaStatus()) || "GC".equalsIgnoreCase(ctx.visaStatus());
        if (!citizenOrGc && isDocumentDetailsMissing(effective)) {
            return new EmployeeStatusResult("Document details missing", null);
        }

        // 7. Otherwise.
        return new EmployeeStatusResult("Compliant", null);
    }

    // Section 3.2's "I-9 Status (auto)" — per-RECORD status, distinct from
    // employeeI9Status (section 7), which is the overall employee-level
    // action-needed value.
    public static String recordStatus(I9Record record) {
        if (SubType.DESTROYED.name().equals(record.getSubtype())) return "Destroyed";
        return record.getCompletedOn() != null ? "Completed" : "In Process";
    }

    // Section 6: "I-9 count for an employee excludes DOC_UPDATE and NAME_CHANGE."
    public static long i9Count(List<I9Record> records) {
        return records.stream()
                .filter(r -> !SubType.DOC_UPDATE.name().equals(r.getSubtype())
                        && !SubType.NAME_CHANGE.name().equals(r.getSubtype()))
                .count();
    }

    private static boolean isDocumentDetailsMissing(I9Record record) {
        boolean hasListA = record.getListA() != null && record.getListA().getCode() != null;
        boolean hasListBC = record.getListB() != null && record.getListB().getCode() != null
                && record.getListC() != null && record.getListC().getCode() != null;
        return !hasListA && !hasListBC;
    }

    // ───────────────────────────── 4. Validation ─────────────────────────────

    public static ValidationResult validate(I9Record record, EmployeeContext ctx, List<I9Record> allRecords) {
        List<Finding> errors = new ArrayList<>();
        List<Finding> warnings = new ArrayList<>();
        SubType sub = record.getSubtype() != null ? SubType.valueOf(record.getSubtype()) : null;

        if (sub == null) {
            errors.add(new Finding("E1", "Choose the I-9 sub type"));
            return new ValidationResult(errors, warnings);
        }
        if (record.getValidFrom() == null) {
            errors.add(new Finding("E2", "Enter the " + validFromLabel(sub)));
        }

        // A receipt (5.5) stands in for the actual document for now — it
        // explicitly replaces the Document Option / List A|B|C choice, not
        // just supplements it, so none of E3-E10 apply while one's present.
        boolean receiptPresented = record.getReceipt() != null && record.getReceipt().getType() != null;

        DocOption option = record.getDocOption() != null ? DocOption.valueOf(record.getDocOption()) : null;
        boolean hasListA = record.getListA() != null && record.getListA().getCode() != null;
        boolean hasListB = record.getListB() != null && record.getListB().getCode() != null;
        boolean hasListC = record.getListC() != null && record.getListC().getCode() != null;

        if (!receiptPresented) {
            boolean needsOption = sub == SubType.NEW_HIRE || sub == SubType.REHIRE_NEW || sub == SubType.REVERIFY
                    || (sub == SubType.REHIRE_SUPP_B && Boolean.TRUE.equals(record.getPriorAuthExpired()));
            if (needsOption && option == null) {
                errors.add(new Finding("E3", "Choose the Document Option"));
            }

            if (option == DocOption.LIST_A) {
                if (!hasListA) errors.add(new Finding("E4", "Pick the List A document"));
                if (hasListB || hasListC) errors.add(new Finding("E5", "Clear List B / C – not used with List A"));
            } else if (option == DocOption.LIST_BC) {
                if (!hasListB || !hasListC) errors.add(new Finding("E6", "Need one List B AND one List C document"));
                if (hasListA) errors.add(new Finding("E7", "Clear List A – not used with List B + C"));
            } else if (option == DocOption.LIST_C_ONLY) {
                if (sub == SubType.NEW_HIRE || sub == SubType.REHIRE_NEW) {
                    errors.add(new Finding("E8", "A new hire needs List A or List B + List C"));
                }
                if (hasListA || hasListB) {
                    errors.add(new Finding("E10", "Clear List A / B – reverification with List C uses only List C"));
                }
                if (!hasListC) errors.add(new Finding("E6", "Need one List B AND one List C document"));
            }

            if ((sub == SubType.REVERIFY || sub == SubType.REHIRE_SUPP_B) && hasListB) {
                errors.add(new Finding("E9", "List B documents cannot be used for reverification – use List A or List C"));
            }
        }

        String listACode = hasListA ? record.getListA().getCode() : null;
        boolean passportBased = listACode != null && Set.of(A_US_PASSPORT, A_FPI, A_FSM_RMI, A_TEMP_I551).contains(listACode);
        if (passportBased || sub == SubType.DOC_UPDATE) {
            boolean missingPassport = ctx.passportNumber() == null || ctx.passportExpiry() == null;
            if (passportBased && missingPassport) {
                errors.add(new Finding("E11", "Enter the Passport Number / Passport Expiry Date"));
            }
        }

        if (A_FPI.equals(listACode) || A_FSM_RMI.equals(listACode)
                || (sub == SubType.DOC_UPDATE) || (C_DHS_EAD.equals(hasListC ? record.getListC().getCode() : null))) {
            var i94 = record.getI94();
            boolean i94Relevant = A_FPI.equals(listACode) || A_FSM_RMI.equals(listACode);
            if (i94Relevant) {
                boolean hasAdmitOrDs = i94 != null && (i94.getAdmitUntil() != null || Boolean.TRUE.equals(i94.getDs()));
                if (i94 == null || i94.getNumber() == null || !hasAdmitOrDs) {
                    errors.add(new Finding("E12", "Enter the I-94 number and admit-until date (or D/S)"));
                } else if (Boolean.TRUE.equals(i94.getDs()) && i94.getAuthEndDate() == null) {
                    errors.add(new Finding("E13", "Enter the authorization end date from the I-20 / EAD"));
                }
            }
        }

        if (A_EAD.equals(listACode) && record.getListA().getExpiry() == null) {
            errors.add(new Finding("E14", "Enter the List A expiration date"));
        }
        if (A_TEMP_I551.equals(listACode) && record.getListA().getExpiry() == null
                && !Boolean.TRUE.equals(record.getListA().getNoExpiry())) {
            errors.add(new Finding("E14", "Enter the List A expiration date"));
        }
        if (hasListC && C_DHS_EAD.equals(record.getListC().getCode()) && record.getListC().getExpiry() == null) {
            errors.add(new Finding("E15", "Enter the List C expiration date"));
        }
        if (hasListC && C_SSN.equals(record.getListC().getCode()) && Boolean.TRUE.equals(record.getListC().getRestrictedSsn())) {
            errors.add(new Finding("E17", "A restricted Social Security card is not an acceptable List C document"));
        }

        if (record.getCompletedOn() != null && record.getSection1Date() != null
                && record.getCompletedOn().isBefore(record.getSection1Date())) {
            errors.add(new Finding("E18", "Section 2 cannot be completed before Section 1"));
        }

        if (sub == SubType.REHIRE_SUPP_B && record.getOriginalI9Date() != null && record.getValidFrom() != null
                && record.getValidFrom().isAfter(record.getOriginalI9Date().plusYears(I9Config.REHIRE_SUPP_B_YEARS))) {
            errors.add(new Finding("E19", "More than 3 years since the original I-9 – complete a New I-9 – Rehire instead"));
        }

        // E16: presented document expired on or before the examination
        // date, with no valid extension or receipt covering it.
        LocalDate examDate = record.getCompletedOn() != null ? record.getCompletedOn() : record.getValidFrom();
        if (examDate != null && record.getExtensionType() == null && record.getReceipt().getType() == null) {
            LocalDate docExpiry = hasListA ? record.getListA().getExpiry()
                    : (hasListC ? record.getListC().getExpiry() : null);
            if (docExpiry != null && docExpiry.isBefore(examDate)) {
                errors.add(new Finding("E16", "Documents must be unexpired on the day they are examined"));
            }
        }

        // E20: the chosen extension's own conditions.
        if (record.getExtensionType() != null) {
            String extError = I9ExtensionCalculator.conditionError(record);
            if (extError != null) errors.add(new Finding("E20", extError));
        }

        if (Boolean.TRUE.equals(record.getRemoteExam())) {
            if (!I9Config.EVERIFY_ENROLLED) {
                errors.add(new Finding("E21", "Remote examination is only allowed for employers enrolled in E-Verify"));
            }
            // E22 (copies of every document examined remotely) is enforced
            // once attachments are available to this call — see
            // I9RecordService, which checks Document rows by fileRole
            // before calling validate() a second time at save time.
        }

        if (Boolean.TRUE.equals(record.getPreparerUsed())) {
            // E23 (Supplement A file present) is likewise checked by
            // I9RecordService against the record's attachments.
        }

        if (sub == SubType.DESTROYED) {
            LocalDate retain = retainUntil(ctx.hireDate(), ctx.lastDay());
            if (retain != null && record.getValidFrom() != null && record.getValidFrom().isBefore(retain)) {
                errors.add(new Finding("E24", "Retention has not ended – keep this I-9 until " + retain));
            }
        }

        if (sub == SubType.CORRECTION && (record.getCorrectsRecordId() == null || record.getCorrectionReason() == null
                || record.getCorrectionReason().isBlank())) {
            errors.add(new Finding("E25", "Pick the record being corrected and give a reason"));
        }

        if (sub == SubType.DOC_UPDATE) {
            boolean anyPassport = ctx.passportNumber() != null || ctx.passportExpiry() != null;
            boolean anyI94 = record.getI94() != null
                    && (record.getI94().getNumber() != null || record.getI94().getAdmitUntil() != null);
            if (!anyPassport && !anyI94) {
                errors.add(new Finding("E26", "Enter the new passport and / or I-94 details"));
            }
        }

        // Warnings.
        if (A_US_PASSPORT.equals(listACode) && !"Citizen".equalsIgnoreCase(ctx.visaStatus())) {
            warnings.add(new Finding("W1", "U.S. passport recorded but Visa Status is " + ctx.visaStatus() + " – please check"));
        }
        if (A_GREEN_CARD.equals(listACode) && !"GC".equalsIgnoreCase(ctx.visaStatus())) {
            warnings.add(new Finding("W2", "Green Card recorded but Visa Status is " + ctx.visaStatus() + " – please check"));
        }
        if (record.getSection1Date() != null && ctx.hireDate() != null && record.getSection1Date().isAfter(ctx.hireDate())) {
            warnings.add(new Finding("W3", "Section 1 was due by the first day of work"));
        }
        LocalDate due = section2Due(record, ctx, null);
        if (due != null && record.getCompletedOn() != null && record.getCompletedOn().isAfter(due)) {
            warnings.add(new Finding("W4", "Completed late (due " + due + ") – add the reason in Additional information"));
        }
        if ("H4".equalsIgnoreCase(ctx.visaStatus())) {
            warnings.add(new Finding("W5", "H-4 without an EAD is not authorized to work – please check"));
        }

        return new ValidationResult(errors, warnings);
    }

    private static String validFromLabel(SubType sub) {
        return switch (sub) {
            case NEW_HIRE, REHIRE_NEW, REHIRE_SUPP_B -> "First day of work (for pay) / Rehire date";
            case REVERIFY -> "Reverification date";
            case NAME_CHANGE -> "Name change date";
            case DOC_UPDATE -> "Date received";
            case CORRECTION -> "Correction date";
            case DESTROYED -> "Destruction date";
            default -> "Valid From date";
        };
    }

    // ───────────────────────────── 8. Tasks ──────────────────────────────────

    public static List<Task> openTasks(EmployeeContext ctx, List<I9Record> records, LocalDate today) {
        List<Task> tasks = new ArrayList<>();
        EmployeeStatusResult status = employeeI9Status(ctx, records, today);

        if (ctx.hasExited()) {
            if (ctx.lastDay() == null || ctx.hireDate() == null) {
                tasks.add(new Task(ctx.employeeId(), TaskType.EXIT_DETAILS_MISSING,
                        "Exit details missing", Priority.LOW, null, "Employee record"));
            } else {
                LocalDate retain = retainUntil(ctx.hireDate(), ctx.lastDay());
                I9Record latest = latestRecord(records);
                boolean destroyed = latest != null && SubType.DESTROYED.name().equals(latest.getSubtype());
                if (retain != null && today.isAfter(retain) && !destroyed) {
                    tasks.add(new Task(ctx.employeeId(), TaskType.I9_RETENTION_ENDED,
                            "I-9 retention ended", Priority.LOW, retain, "Destroy securely, then add DESTROYED"));
                }
            }
            return tasks;
        }

        if (status.status() != null) {
            switch (status.status()) {
                case "New I-9 needed", "New I-9 OVERDUE", "Rehire I-9 needed", "Rehire I-9 OVERDUE", "No I-9 on file" ->
                        tasks.add(new Task(ctx.employeeId(), TaskType.NEW_I9_NEEDED,
                                status.status(), Priority.HIGH, status.dueDate(), "Add a NEW_HIRE / rehire record"));
                case "I-9 in progress", "I-9 OVERDUE" ->
                        tasks.add(new Task(ctx.employeeId(), TaskType.I9_INCOMPLETE,
                                status.status(), Priority.HIGH, status.dueDate(), "Fill the Section 2 / Supplement B date"));
                case "Reverification due", "Reverification OVERDUE" -> {
                    boolean highPriority = "Reverification OVERDUE".equals(status.status())
                            || (status.dueDate() != null
                            && ChronoUnit.DAYS.between(today, status.dueDate()) <= I9Config.HIGH_PRIORITY_DAYS);
                    tasks.add(new Task(ctx.employeeId(), TaskType.I9_REVERIFICATION,
                            status.status(), highPriority ? Priority.HIGH : Priority.MEDIUM, status.dueDate(),
                            "Add a REVERIFY record"));
                }
                case "Expiry date missing", "Document details missing" ->
                        tasks.add(new Task(ctx.employeeId(), TaskType.I9_DETAILS_MISSING,
                                status.status(), Priority.MEDIUM, null, "Edit the latest record"));
                default -> {
                    // Compliant: nothing to surface.
                }
            }
        }

        I9Record latest = latestRecord(records);
        if (latest != null) {
            I9Record effective = effectiveRecord(latest, records);
            String result = effective.getEverify() != null ? effective.getEverify().getResult() : null;
            boolean reverifyTab = SubType.REVERIFY.name().equals(effective.getSubtype());
            if (!reverifyTab && I9Config.EVERIFY_ENROLLED
                    && result != null && !Set.of("AUTHORIZED", "CLOSED", "NA").contains(result)) {
                boolean mismatch = "TNC_MISMATCH".equals(result);
                tasks.add(new Task(ctx.employeeId(), TaskType.EVERIFY_PENDING, "E-Verify pending",
                        mismatch ? Priority.HIGH : Priority.MEDIUM, null, "Latest record → E-Verify"));
            }

            if (effective.getReceipt() != null && effective.getReceipt().getType() != null
                    && effective.getReceipt().getValidUntil() != null) {
                tasks.add(new Task(ctx.employeeId(), TaskType.RECEIPT_FOLLOWUP, "Receipt follow-up",
                        Priority.HIGH, effective.getReceipt().getValidUntil(), "Record the real document"));
            }
        }

        if (ctx.passportExpiry() != null) {
            long daysLeft = ChronoUnit.DAYS.between(today, ctx.passportExpiry());
            if (daysLeft <= I9Config.PASSPORT_ALERT_DAYS) {
                boolean expired = ctx.passportExpiry().isBefore(today);
                tasks.add(new Task(ctx.employeeId(), TaskType.PASSPORT_EXPIRY, "Passport expiry",
                        expired ? Priority.HIGH : Priority.MEDIUM, ctx.passportExpiry(), "DOC_UPDATE with the new passport"));
            }
        }

        if (latest != null) {
            I9Record effective = effectiveRecord(latest, records);
            LocalDate admitUntil = effective.getI94() != null ? effective.getI94().getAdmitUntil() : null;
            if (admitUntil != null) {
                long daysLeft = ChronoUnit.DAYS.between(today, admitUntil);
                if (daysLeft <= I9Config.I94_ALERT_DAYS) {
                    boolean expired = admitUntil.isBefore(today);
                    tasks.add(new Task(ctx.employeeId(), TaskType.I94_EXPIRY, "I-94 expiry",
                            expired ? Priority.HIGH : Priority.MEDIUM, admitUntil, "DOC_UPDATE / REVERIFY"));
                }
            }
        }

        return tasks;
    }
}
