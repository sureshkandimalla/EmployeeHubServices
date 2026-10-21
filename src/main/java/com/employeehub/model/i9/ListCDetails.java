package com.employeehub.model.i9;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Embeddable;
import java.time.LocalDate;

// List C work-authorization document. code = C_DHS_EAD (List C #7, e.g.
// an I-94 with an L-2S/E-1S/E-2S/E-3S annotation) is the only one that
// expires and is ever reverified; C_SSN with restrictedSsn=true is never
// an acceptable document (I9Rules E17).
@Setter
@Getter
@Embeddable
public class ListCDetails {
    private String code;
    private String number;
    private LocalDate expiry;
    private Boolean restrictedSsn;
}
