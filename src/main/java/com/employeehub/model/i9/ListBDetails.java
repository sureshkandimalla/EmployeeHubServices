package com.employeehub.model.i9;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Embeddable;
import java.time.LocalDate;

// List B identity document (driver's license, state ID, ...). Never used
// with docOption = LIST_A or LIST_C_ONLY (I9Rules enforces E5/E7/E9/E10).
@Setter
@Getter
@Embeddable
public class ListBDetails {
    private String code;
    private String number;
    private LocalDate expiry;
}
