package com.employeehub.model.i9;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Embeddable;
import java.time.LocalDate;

// result is one of AUTHORIZED, TNC_MISMATCH, CONTINUANCE, CLOSE_RESUBMIT,
// FINAL_NC, CLOSED, NA (not enrolled). Hidden entirely on REVERIFY per the
// spec (E-Verify is never used for reverification).
@Setter
@Getter
@Embeddable
public class EverifyDetails {
    private String caseNo;
    private LocalDate created;
    private String result;
}
