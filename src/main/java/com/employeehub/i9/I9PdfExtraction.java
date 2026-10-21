package com.employeehub.i9;

import java.time.LocalDate;
import java.util.List;

// What I9PdfExtractor pulls out of a filled official Form I-9 PDF —
// deliberately a flat, nullable-everything DTO: any field it couldn't
// confidently read is left null rather than guessed, and the frontend
// treats every non-null value as "pre-filled, please verify" (never
// auto-saved — this only ever feeds a form the user still has to review
// and submit themselves).
public record I9PdfExtraction(
        // Shown to the HR user as a sanity check against the employee
        // they're actually adding this record for — see
        // I9RecordController#extractFromPdf's nameMismatch check.
        String extractedFirstName,
        String extractedLastName,

        LocalDate validFrom,
        LocalDate section1Date,
        String attestation,
        LocalDate section1AuthExpiry,
        LocalDate completedOn,

        String docOption,
        String listACode,
        String listANumber,
        LocalDate listAExpiry,
        String i94Number,
        LocalDate i94AdmitUntil,
        String listBCode,
        String listBNumber,
        LocalDate listBExpiry,
        String listCCode,
        String listCNumber,
        LocalDate listCExpiry,

        String passportNumber,
        LocalDate passportExpiry,

        // Which of the above fields actually got populated — drives the
        // frontend's "extracted from PDF" highlighting without it having
        // to separately diff against null.
        List<String> populatedFields
) {
}
