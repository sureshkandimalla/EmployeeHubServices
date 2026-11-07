package com.employeehub.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the per-invoice-term period math against the same rules
 * ProjectService#createProjectForInvoice already implements (see that
 * class's own Monthly/Semi-Monthly/week-aligned branches) — this is an
 * independent copy for the Timesheet vs Invoice report, so these tests
 * exist to catch the two copies drifting apart.
 */
class InvoicePeriodCalculatorTest {

    @Test
    void weekly_isAnchoredToWeekStartDayAndSpansSevenDays() {
        // Range starts on a Wednesday; anchor is Monday, so the first
        // period should snap back to the preceding Monday. A period whose
        // start falls on or before rangeEnd is included even if it runs
        // past rangeEnd — same "cursor not after rangeEnd" rule
        // ProjectService#addWeekAlignedRows uses.
        List<InvoicePeriodCalculator.Period> periods = InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 20), "1", DayOfWeek.MONDAY);

        assertEquals(3, periods.size());
        assertEquals(LocalDate.of(2026, 1, 7), periods.get(0).start); // clipped forward to rangeStart
        assertEquals(LocalDate.of(2026, 1, 11), periods.get(0).end);
        assertEquals(LocalDate.of(2026, 1, 12), periods.get(1).start);
        assertEquals(LocalDate.of(2026, 1, 18), periods.get(1).end);
        assertEquals(LocalDate.of(2026, 1, 19), periods.get(2).start);
        assertEquals(LocalDate.of(2026, 1, 25), periods.get(2).end);
    }

    @Test
    void biweekly_spansFourteenDays() {
        List<InvoicePeriodCalculator.Period> periods = InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 25), "2", DayOfWeek.MONDAY);

        assertEquals(2, periods.size());
        assertEquals(LocalDate.of(2026, 1, 5), periods.get(0).start);
        assertEquals(LocalDate.of(2026, 1, 18), periods.get(0).end);
        assertEquals(LocalDate.of(2026, 1, 19), periods.get(1).start);
        assertEquals(LocalDate.of(2026, 2, 1), periods.get(1).end);
    }

    @Test
    void onceInFourWeeks_spansTwentyEightDays() {
        List<InvoicePeriodCalculator.Period> periods = InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), "6", DayOfWeek.MONDAY);

        assertEquals(1, periods.size());
        assertEquals(LocalDate.of(2026, 1, 5), periods.get(0).start);
        assertEquals(LocalDate.of(2026, 2, 1), periods.get(0).end);
    }

    @Test
    void semiMonthly_splitsAtTheFifteenth() {
        List<InvoicePeriodCalculator.Period> periods = InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "5", DayOfWeek.MONDAY);

        assertEquals(2, periods.size());
        assertEquals(LocalDate.of(2026, 2, 1), periods.get(0).start);
        assertEquals(LocalDate.of(2026, 2, 15), periods.get(0).end);
        assertEquals(LocalDate.of(2026, 2, 16), periods.get(1).start);
        assertEquals(LocalDate.of(2026, 2, 28), periods.get(1).end); // Feb 2026 is not a leap year
    }

    @Test
    void monthly_spansTheFullCalendarMonth() {
        // The cursor advances by a flat plusMonths(1) off rangeStart itself
        // (Mar 10 -> Apr 10), not off the 1st of the period -- so a
        // rangeEnd that falls earlier in the next month (Apr 5) never gets
        // its own period. Same quirk already present in
        // ProjectService#createProjectForInvoice's Monthly branch.
        List<InvoicePeriodCalculator.Period> periods = InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 5), "3", DayOfWeek.MONDAY);

        assertEquals(1, periods.size());
        assertEquals(LocalDate.of(2026, 3, 1), periods.get(0).start);
        assertEquals(LocalDate.of(2026, 3, 31), periods.get(0).end);

        // A rangeEnd comfortably into the next month does get both periods.
        List<InvoicePeriodCalculator.Period> wider = InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 15), "3", DayOfWeek.MONDAY);
        assertEquals(2, wider.size());
        assertEquals(LocalDate.of(2026, 4, 1), wider.get(1).start);
        assertEquals(LocalDate.of(2026, 4, 30), wider.get(1).end);
    }

    @Test
    void unrecognizedTerm_fallsBackToMonthly() {
        List<InvoicePeriodCalculator.Period> periods = InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), "4", DayOfWeek.MONDAY);

        assertEquals(1, periods.size());
        assertEquals(LocalDate.of(2026, 3, 1), periods.get(0).start);
        assertEquals(LocalDate.of(2026, 3, 31), periods.get(0).end);
    }

    @Test
    void resolveWeekStartDay_defaultsToMondayWhenBlankOrInvalid() {
        assertEquals(DayOfWeek.MONDAY, InvoicePeriodCalculator.resolveWeekStartDay(null));
        assertEquals(DayOfWeek.MONDAY, InvoicePeriodCalculator.resolveWeekStartDay(""));
        assertEquals(DayOfWeek.MONDAY, InvoicePeriodCalculator.resolveWeekStartDay("not-a-day"));
        assertEquals(DayOfWeek.SUNDAY, InvoicePeriodCalculator.resolveWeekStartDay("sunday"));
    }

    @Test
    void invalidRange_returnsNoPeriods() {
        assertTrue(InvoicePeriodCalculator.computePeriods(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1), "3", DayOfWeek.MONDAY).isEmpty());
        assertTrue(InvoicePeriodCalculator.computePeriods(null, LocalDate.of(2026, 3, 1), "3", DayOfWeek.MONDAY).isEmpty());
    }
}
