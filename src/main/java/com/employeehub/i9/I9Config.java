package com.employeehub.i9;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// Legal numbers used by I9Rules, kept in one place because USCIS rules
// change. Mirrors the JS i9Config.ts constants 1:1 — see i9-rules.md for
// the rules these back. No HR-editable settings screen exists yet, so this
// is a static source of truth rather than a per-tenant DB-backed config.
public final class I9Config {

    private I9Config() {
    }

    public static final int SECTION2_BUSINESS_DAYS = 3;
    public static final int EVERIFY_BUSINESS_DAYS = 3;
    // Empty = Mon-Fri only, matching the spreadsheet's WORKDAY() function.
    public static final Set<LocalDate> HOLIDAYS = Set.of();
    public static final int REHIRE_SUPP_B_YEARS = 3;
    public static final int RETENTION_YEARS_AFTER_HIRE = 3;
    public static final int RETENTION_YEARS_AFTER_EXIT = 1;
    public static final int EAD_AUTO_EXT_DAYS = 540;
    // Renewals received on/after this date get no automatic extension.
    public static final LocalDate EAD_AUTO_EXT_CUTOFF = LocalDate.of(2025, 10, 30);
    public static final Set<String> SPOUSE_EAD_CATEGORIES = Set.of("A17", "A18", "C26");
    public static final int STEM_OPT_EXT_DAYS = 180;
    public static final int H1B_240_DAYS = 240;
    public static final int RECEIPT_REPLACEMENT_DAYS = 90;
    public static final int REFUGEE_RECEIPT_DAYS = 90;
    public static final int TEMP_I551_NO_EXPIRY_DAYS = 365;
    public static final int I9_REVERIFY_ALERT_DAYS = 90;
    public static final int PASSPORT_ALERT_DAYS = 180;
    public static final int I94_ALERT_DAYS = 60;
    public static final int HIGH_PRIORITY_DAYS = 30;
    public static boolean EVERIFY_ENROLLED = true;
    public static final boolean COPY_DOCUMENTS_FOR_ALL = true;

    public record Edition(String edition, LocalDate expires) {
    }

    public static final List<Edition> ALLOWED_EDITIONS = List.of(
            new Edition("01/20/25", LocalDate.of(2027, 5, 31)),
            new Edition("08/01/23", LocalDate.of(2027, 5, 31))
    );
}
