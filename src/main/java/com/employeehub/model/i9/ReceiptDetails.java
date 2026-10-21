package com.employeehub.model.i9;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Embeddable;
import java.time.LocalDate;

// A receipt accepted in place of a document (spec section 5.5) — never
// changes reverifyBy by itself, it just creates a "Receipt follow-up"
// task due on validUntil. type is one of REPLACEMENT, I94_TEMP_I551,
// I94_REFUGEE (I9Rules.ReceiptType).
@Setter
@Getter
@Embeddable
public class ReceiptDetails {
    private String type;
    private LocalDate validUntil;
}
