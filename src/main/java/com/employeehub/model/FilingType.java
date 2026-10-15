package com.employeehub.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilingType {
    REGULAR("Regular"),
    TRANSFER("Transfer"),
    CONSULAR("Consular"),
    COS("Change of Status");

    private final String displayName;

    FilingType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static FilingType fromString(String value) {
        if (value == null) return null;
        for (FilingType f : values()) {
            if (f.displayName.equalsIgnoreCase(value) || f.name().equalsIgnoreCase(value)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unknown FilingType: " + value);
    }
}
