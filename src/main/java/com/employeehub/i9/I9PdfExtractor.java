package com.employeehub.i9;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// Reads field values straight out of the official fillable Form I-9 PDF
// (08/01/23 and 01/20/25 editions — both share the same AcroForm field
// names; verified against a real filled copy). This only works because
// the government PDF has real named form fields — confirmed by inspecting
// one: 20 of its 124 fields carried the employee's actual data, the rest
// (mostly the employer/signature portion, which many e-sign tools stamp
// in as a flattened overlay rather than filling the native field) were
// blank despite showing visible text on the page. Deterministic field
// reads beat trying to parse free-form text positions, and need no
// external AI/OCR service.
//
// A field that isn't present, or isn't filled, is simply left out of the
// result (never guessed) — see I9PdfExtraction's javadoc.
public final class I9PdfExtractor {

    private I9PdfExtractor() {
    }

    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    // Section 1's attestation checkboxes, in item-number order.
    private static final String[] ATTESTATION_CHECKBOXES = {"CB_1", "CB_2", "CB_3", "CB_4"};
    private static final String[] ATTESTATION_CODES = {
            I9Rules.Attestation.CITIZEN.name(), I9Rules.Attestation.NONCITIZEN_NATIONAL.name(),
            I9Rules.Attestation.LPR.name(), I9Rules.Attestation.NONCITIZEN_AUTHORIZED.name(),
    };

    public static I9PdfExtraction extract(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) {
                return new I9PdfExtraction(null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, List.of());
            }

            List<String> populated = new ArrayList<>();
            Function<String, String> text = (name) -> {
                String v = textValue(form, name);
                if (v != null && !v.isBlank()) populated.add(name);
                return v;
            };
            Function<String, LocalDate> date = (name) -> {
                LocalDate v = dateValue(form, name);
                if (v != null) populated.add(name);
                return v;
            };

            String lastName = text.apply("Last Name (Family Name)");
            String firstName = text.apply("First Name Given Name");

            LocalDate validFrom = date.apply("FirstDayEmployed mmddyyyy");
            LocalDate section1Date = text.apply("Today's Date mmddyyy") != null
                    ? dateValue(form, "Today's Date mmddyyy") : null;
            LocalDate completedOn = date.apply("S2 Todays Date mmddyyyy");

            String attestation = attestationFromCheckboxes(form);
            LocalDate section1AuthExpiry = date.apply("Exp Date mmddyyyy");

            String docTitle1 = text.apply("Document Title 1");
            String docTitle2 = text.apply("Document Title 2 If any");
            boolean hasListA = docTitle1 != null && !docTitle1.isBlank();
            boolean hasI94 = docTitle2 != null && docTitle2.toLowerCase().contains("i94")
                    || docTitle2 != null && docTitle2.toLowerCase().contains("i-94");

            String listACode = hasListA ? listACodeFor(docTitle1, hasI94) : null;
            String listANumber = text.apply("Document Number 0 (if any)");
            LocalDate listAExpiry = date.apply("Expiration Date if any");

            String i94Number = hasI94 ? text.apply("Document Number If any_2") : null;
            LocalDate i94AdmitUntil = hasI94 ? section1AuthExpiry : null;

            String listBTitle = text.apply("List B Document 1 Title");
            String listBCode = listBTitle != null && !listBTitle.isBlank() ? listBCodeFor(listBTitle) : null;
            String listBNumber = text.apply("List B Document Number 1");
            LocalDate listBExpiry = date.apply("List B Expiration Date 1");

            String listCTitle = text.apply("List C Document Title 1");
            String listCCode = listCTitle != null && !listCTitle.isBlank() ? listCCodeFor(listCTitle) : null;
            String listCNumber = text.apply("List C Document Number 1");
            LocalDate listCExpiry = date.apply("List C Expiration Date 1");

            String docOption = hasListA ? I9Rules.DocOption.LIST_A.name()
                    : (listBCode != null || listCCode != null) ? I9Rules.DocOption.LIST_BC.name() : null;

            boolean passportLike = I9Rules.A_US_PASSPORT.equals(listACode) || I9Rules.A_FPI.equals(listACode)
                    || I9Rules.A_FSM_RMI.equals(listACode) || I9Rules.A_TEMP_I551.equals(listACode);
            String passportNumber = passportLike
                    ? coalesceText(form, "Foreign Passport Number and Country of IssuanceRow1", listANumber)
                    : null;
            LocalDate passportExpiry = passportLike ? listAExpiry : null;

            return new I9PdfExtraction(firstName, lastName, validFrom, section1Date, attestation, section1AuthExpiry,
                    completedOn, docOption, listACode, listANumber, listAExpiry, i94Number, i94AdmitUntil,
                    listBCode, listBNumber, listBExpiry, listCCode, listCNumber, listCExpiry,
                    passportNumber, passportExpiry, populated);
        }
    }

    private static String attestationFromCheckboxes(PDAcroForm form) {
        for (int i = 0; i < ATTESTATION_CHECKBOXES.length; i++) {
            PDField field = form.getField(ATTESTATION_CHECKBOXES[i]);
            if (field instanceof PDCheckBox checkBox && checkBox.isChecked()) {
                return ATTESTATION_CODES[i];
            }
        }
        return null;
    }

    private static String textValue(PDAcroForm form, String name) {
        PDField field = form.getField(name);
        if (field == null) return null;
        String v = field.getValueAsString();
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String coalesceText(PDAcroForm form, String preferredFieldName, String fallback) {
        String v = textValue(form, preferredFieldName);
        return v != null ? v : fallback;
    }

    private static LocalDate dateValue(PDAcroForm form, String name) {
        String raw = textValue(form, name);
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw, MDY);
        } catch (Exception e) {
            return null; // Partially-typed or malformed date — leave for manual entry rather than guess.
        }
    }

    // Keyword match against the typed document title text on the PDF —
    // confident matches only; anything unrecognized falls back to
    // A_OTHER/null rather than risk a wrong compliance-relevant document
    // code.
    private static String listACodeFor(String title, boolean hasI94) {
        String t = title.toLowerCase();
        if (t.contains("foreign passport")) return hasI94 ? I9Rules.A_FPI : I9Rules.A_OTHER;
        if (t.contains("passport")) return I9Rules.A_US_PASSPORT;
        if (t.contains("permanent resident") || t.contains("i-551") || t.contains("i551") || t.contains("green card")) {
            return I9Rules.A_GREEN_CARD;
        }
        if (t.contains("employment authorization") || t.contains("i-766") || t.contains("i766") || t.contains("ead")) {
            return I9Rules.A_EAD;
        }
        if (t.contains("fsm") || t.contains("rmi") || t.contains("micronesia") || t.contains("marshall")) {
            return I9Rules.A_FSM_RMI;
        }
        if (t.contains("temporary i-551") || t.contains("temporary i551") || t.contains("temp i-551")) {
            return I9Rules.A_TEMP_I551;
        }
        return I9Rules.A_OTHER;
    }

    private static String listBCodeFor(String title) {
        String t = title.toLowerCase();
        if (t.contains("driver") || t.contains("state id")) return "B_DL";
        if (t.contains("school")) return "B_SCHOOL_ID";
        if (t.contains("voter")) return "B_VOTER";
        if (t.contains("military dependent")) return "B_MILITARY_DEP";
        if (t.contains("military") || t.contains("draft")) return "B_MILITARY";
        if (t.contains("merchant mariner") || t.contains("coast guard")) return "B_MERCHANT_MARINER";
        if (t.contains("tribal")) return "B_TRIBAL";
        if (t.contains("canadian")) return "B_CANADIAN_DL";
        if (t.contains("federal") || t.contains("government")) return "B_FEDERAL_ID";
        return null; // Not confident — leave for the user to pick.
    }

    private static String listCCodeFor(String title) {
        String t = title.toLowerCase();
        if (t.contains("social security")) return I9Rules.C_SSN;
        if (t.contains("birth certificate")) return "C_BIRTH_CERT";
        if (t.contains("report of birth") || t.contains("fs-545") || t.contains("ds-1350") || t.contains("fs-240")) {
            return "C_BIRTH_CERT_STATE";
        }
        if (t.contains("tribal")) return "C_TRIBAL";
        if (t.contains("i-197") || t.contains("i197")) return "C_I197";
        if (t.contains("i-179") || t.contains("i179")) return "C_I179";
        if (t.contains("employment authorization") || t.contains("dhs")) return I9Rules.C_DHS_EAD;
        return null; // Not confident — leave for the user to pick.
    }
}
