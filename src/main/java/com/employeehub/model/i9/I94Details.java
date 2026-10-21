package com.employeehub.model.i9;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Embeddable;
import java.time.LocalDate;

// Form I-94 admission record — present alongside a foreign passport (List
// A #5/#6) or a DHS-issued EAD (List C #7). "D/S" (Duration of Status)
// means no fixed admit-until date; authEndDate (from the I-20/EAD) stands
// in for it instead.
@Setter
@Getter
@Embeddable
public class I94Details {
    private String number;
    private LocalDate admitUntil;
    private Boolean ds;
    private LocalDate authEndDate;
}
