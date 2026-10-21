package com.employeehub.exception;

import com.employeehub.i9.I9Rules;

// Carries the full error/warning list from I9Rules.validate so
// I9RecordController can return it as structured JSON (422) instead of a
// single plain-text message — the frontend renders each Finding inline
// next to the field it's about, per the spec's "errors appear inline and
// block Upload" requirement.
public class I9ValidationException extends RuntimeException {

    private final I9Rules.ValidationResult result;

    public I9ValidationException(I9Rules.ValidationResult result) {
        super("I-9 record failed validation: " + result.errors());
        this.result = result;
    }

    public I9Rules.ValidationResult getResult() {
        return result;
    }
}
