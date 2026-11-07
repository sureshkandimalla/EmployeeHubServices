package com.employeehub.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

// Computes a project's invoice periods (start/end date pairs) for a given
// invoice-term code, mirroring the per-term branching already implemented in
// ProjectService#createProjectForInvoice/#addWeekAlignedRows — kept as an
// independent copy here (same convention this codebase already uses between
// the backend Java version and the frontend JS version in invoiceTerm.js:
// computeInvoicePeriod) rather than refactoring the invoice-generation code
// path, since that path is load-bearing for real billing and this one only
// backs a read-only report.
public final class InvoicePeriodCalculator {

    private InvoicePeriodCalculator() {
    }

    public static final class Period {
        public final LocalDate start;
        public final LocalDate end;

        public Period(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }
    }

    // Same fallback rule every other reader of Project#weekStartDay uses:
    // null/blank/unparseable all default to Monday.
    public static DayOfWeek resolveWeekStartDay(String configured) {
        if (configured == null || configured.isBlank()) {
            return DayOfWeek.MONDAY;
        }
        try {
            return DayOfWeek.valueOf(configured.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DayOfWeek.MONDAY;
        }
    }

    // Every period that starts within [rangeStart, rangeEnd], inclusive.
    // invoiceTerm is the numeric code string ("1".."6"); unrecognized codes
    // (including "4" / Special) fall back to Monthly, matching
    // ProjectService's own fallback.
    public static List<Period> computePeriods(LocalDate rangeStart, LocalDate rangeEnd, String invoiceTerm, DayOfWeek weekStartDay) {
        List<Period> periods = new ArrayList<>();
        if (rangeStart == null || rangeEnd == null || rangeStart.isAfter(rangeEnd)) {
            return periods;
        }

        if ("1".equals(invoiceTerm)) {
            addWeekAlignedPeriods(periods, rangeStart, rangeEnd, weekStartDay, 1, 6);
        } else if ("2".equals(invoiceTerm)) {
            addWeekAlignedPeriods(periods, rangeStart, rangeEnd, weekStartDay, 2, 13);
        } else if ("6".equals(invoiceTerm)) {
            addWeekAlignedPeriods(periods, rangeStart, rangeEnd, weekStartDay, 4, 27);
        } else if ("5".equals(invoiceTerm)) {
            LocalDate monthCursor = rangeStart.withDayOfMonth(1);
            while (!monthCursor.isAfter(rangeEnd)) {
                LocalDate firstHalfStart = monthCursor;
                LocalDate firstHalfEnd = monthCursor.withDayOfMonth(15);
                periods.add(new Period(firstHalfStart, firstHalfEnd));

                LocalDate secondHalfStart = monthCursor.withDayOfMonth(16);
                LocalDate secondHalfEnd = monthCursor.withDayOfMonth(monthCursor.lengthOfMonth());
                periods.add(new Period(secondHalfStart, secondHalfEnd));

                monthCursor = monthCursor.plusMonths(1);
            }
        } else {
            LocalDate current = rangeStart;
            while (!current.isAfter(rangeEnd)) {
                LocalDate periodStart = current.withDayOfMonth(1);
                LocalDate periodEnd = current.withDayOfMonth(current.lengthOfMonth());
                periods.add(new Period(periodStart, periodEnd));
                current = current.plusMonths(1);
            }
        }

        return periods;
    }

    private static void addWeekAlignedPeriods(List<Period> periods, LocalDate rangeStart, LocalDate rangeEnd,
            DayOfWeek weekStartDay, int stepWeeks, int spanDays) {
        LocalDate cursor = rangeStart.with(TemporalAdjusters.previousOrSame(weekStartDay));
        boolean isFirstRow = true;
        while (!cursor.isAfter(rangeEnd)) {
            LocalDate periodStart = (isFirstRow && cursor.isBefore(rangeStart)) ? rangeStart : cursor;
            LocalDate periodEnd = cursor.plusDays(spanDays);
            isFirstRow = false;
            periods.add(new Period(periodStart, periodEnd));
            cursor = cursor.plusWeeks(stepWeeks);
        }
    }
}
