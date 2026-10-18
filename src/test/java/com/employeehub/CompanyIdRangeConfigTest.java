package com.employeehub;

import com.employeehub.config.CompanyIdRangeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reserved employeeId ranges decide which company a brand new employee's
 * id belongs to — overlapping or shifted ranges would hand an Intellan id to
 * a Code9 hire, so each range is pinned here.
 */
class CompanyIdRangeConfigTest {

    private CompanyIdRangeConfig config;

    @BeforeEach
    void setUp() {
        config = new CompanyIdRangeConfig();
    }

    @Test
    @DisplayName("getRangeFor: Intellan's standard range is 1-100")
    void getRangeFor_intellan() {
        assertArrayEquals(new long[]{1, 100}, config.getRangeFor("Intellan Technologies LLC"));
    }

    @Test
    @DisplayName("getRangeFor: Code9's standard range is 101-200")
    void getRangeFor_code9() {
        assertArrayEquals(new long[]{101, 200}, config.getRangeFor("Code9 LLC"));
    }

    @Test
    @DisplayName("getRangeFor: Referral's standard range is 900-949")
    void getRangeFor_referral() {
        assertArrayEquals(new long[]{900, 949}, config.getRangeFor("Referral"));
    }

    @Test
    @DisplayName("getNewHireRangeFor: Intellan's new-hire range is 500-599")
    void getNewHireRangeFor_intellan() {
        assertArrayEquals(new long[]{500, 599}, config.getNewHireRangeFor("Intellan Technologies LLC"));
    }

    @Test
    @DisplayName("getNewHireRangeFor: Code9's new-hire range is 600-699")
    void getNewHireRangeFor_code9() {
        assertArrayEquals(new long[]{600, 699}, config.getNewHireRangeFor("Code9 LLC"));
    }

    @Test
    @DisplayName("getNewHireRangeFor: Referral's new-hire range is 950-999")
    void getNewHireRangeFor_referral() {
        assertArrayEquals(new long[]{950, 999}, config.getNewHireRangeFor("Referral"));
    }

    @Test
    @DisplayName("getRangeFor: returns null for a company with no configured range")
    void getRangeFor_unknownCompany() {
        assertNull(config.getRangeFor("Some Other Co"));
    }

    @Test
    @DisplayName("getNewHireRangeFor: returns null for a company with no configured new-hire range")
    void getNewHireRangeFor_unknownCompany() {
        assertNull(config.getNewHireRangeFor("Some Other Co"));
    }

    @Test
    @DisplayName("getRangeFor: company names are matched exactly, so a differently-cased name has no range")
    void getRangeFor_isCaseSensitive() {
        assertNull(config.getRangeFor("code9 llc"));
    }

    @Test
    @DisplayName("getRangeFor: rejects a null company name (callers must null-check first)")
    void getRangeFor_nullCompany() {
        // Backed by Map.of(), which forbids null lookups. Not a defect: the
        // only caller (EmployeeService#saveEmployeeDetails) already guards on
        // companyName != null && !isEmpty before asking for a range. Pinned
        // so that guard can't be dropped without a test noticing.
        assertThrows(NullPointerException.class, () -> config.getRangeFor(null));
    }

    @Test
    @DisplayName("standard and new-hire ranges never overlap for the same company")
    void ranges_doNotOverlap() {
        for (String company : new String[]{"Intellan Technologies LLC", "Code9 LLC", "Referral"}) {
            long[] standard = config.getRangeFor(company);
            long[] newHire = config.getNewHireRangeFor(company);
            assertTrue(standard[1] < newHire[0] || newHire[1] < standard[0],
                    company + ": standard and new-hire ranges overlap");
        }
    }

    @Test
    @DisplayName("no two companies' standard ranges overlap")
    void standardRanges_doNotOverlapAcrossCompanies() {
        long[] intellan = config.getRangeFor("Intellan Technologies LLC");
        long[] code9 = config.getRangeFor("Code9 LLC");
        long[] referral = config.getRangeFor("Referral");

        assertTrue(intellan[1] < code9[0]);
        assertTrue(code9[1] < referral[0]);
    }
}
