package com.employeehub.model.i9;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Embeddable;
import java.time.LocalDate;

// List A document (identity + work authorization) — codes match
// I9Rules.ListACode (A_US_PASSPORT, A_GREEN_CARD, A_TEMP_I551, A_EAD,
// A_FPI, A_FSM_RMI, A_OTHER). Used only when docOption = LIST_A.
@Setter
@Getter
@Embeddable
public class ListADetails {
    private String code;
    private String number;
    private LocalDate expiry;
    // Allowed only for A_GREEN_CARD (I9Rules enforces this on validate).
    private Boolean noExpiry;
}
