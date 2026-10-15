package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VisaSubCategory {
    EXTENSION("Extension"),
    AMENDMENT_EXTENSION("Amendment + Extension"),
    AMENDMENT("Amendment"),
    TRANSFER("Transfer"),
    CONCURRENT("Concurrent"),
    CAP("CAP"),
    TRANSFER_INDIA("Transfer-India");

    private final String displayName;

    VisaSubCategory(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static VisaSubCategory fromString(String value) {
        if (value == null) return null;
        for (VisaSubCategory v : values()) {
            if (v.displayName.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown VisaSubCategory: " + value);
    }
}
