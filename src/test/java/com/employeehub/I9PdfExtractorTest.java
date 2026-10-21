package com.employeehub;

import com.employeehub.i9.I9PdfExtraction;
import com.employeehub.i9.I9PdfExtractor;
import com.employeehub.i9.I9Rules;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds a minimal synthetic PDF (same AcroForm field names as the real
 * official Form I-9, but no real employee's data — verified separately,
 * by hand, against an actual filled copy) so this gets committed
 * regression coverage without checking any real PII into the repo.
 */
class I9PdfExtractorTest {

    private byte[] buildI9Pdf(java.util.Map<String, String> textFields, String checkedBox) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDAcroForm acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);
            acroForm.setDefaultResources(new org.apache.pdfbox.pdmodel.PDResources());
            acroForm.getDefaultResources().put(org.apache.pdfbox.cos.COSName.getPDFName("Helv"), PDType1Font.HELVETICA);
            acroForm.setDefaultAppearance("/Helv 10 Tf 0 g");

            for (var entry : textFields.entrySet()) {
                PDTextField field = new PDTextField(acroForm);
                field.setPartialName(entry.getKey());
                field.getWidgets().get(0).setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(0, 0, 100, 20));
                field.getWidgets().get(0).setPage(page);
                page.getAnnotations().add(field.getWidgets().get(0));
                acroForm.getFields().add(field);
                field.setValue(entry.getValue());
            }

            for (String box : new String[]{"CB_1", "CB_2", "CB_3", "CB_4"}) {
                PDCheckBox checkBox = new PDCheckBox(acroForm);
                checkBox.setPartialName(box);
                checkBox.getWidgets().get(0).setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(0, 0, 20, 20));
                checkBox.getWidgets().get(0).setPage(page);
                page.getAnnotations().add(checkBox.getWidgets().get(0));
                acroForm.getFields().add(checkBox);
                if (box.equals(checkedBox)) checkBox.check();
                else checkBox.unCheck();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsNewHireForeignPassportPlusI94() throws Exception {
        byte[] pdf = buildI9Pdf(java.util.Map.of(
                "Last Name (Family Name)", "Doe",
                "First Name Given Name", "Jane",
                "FirstDayEmployed mmddyyyy", "01/15/2026",
                "Today's Date mmddyyy", "01/15/2026",
                "Exp Date mmddyyyy", "06/30/2027",
                "Document Title 1", "Foreign Passport",
                "Document Title 2 If any", "i94",
                "Document Number 0 (if any)", "P12345",
                "Document Number If any_2", "I94-98765",
                "Expiration Date if any", "12/01/2030"
        ), "CB_4");

        I9PdfExtraction result = I9PdfExtractor.extract(new ByteArrayInputStream(pdf));

        assertEquals("Jane", result.extractedFirstName());
        assertEquals("Doe", result.extractedLastName());
        assertEquals(LocalDate.of(2026, 1, 15), result.validFrom());
        assertEquals(LocalDate.of(2026, 1, 15), result.section1Date());
        assertEquals(I9Rules.Attestation.NONCITIZEN_AUTHORIZED.name(), result.attestation());
        assertEquals(LocalDate.of(2027, 6, 30), result.section1AuthExpiry());
        assertEquals(I9Rules.DocOption.LIST_A.name(), result.docOption());
        assertEquals(I9Rules.A_FPI, result.listACode());
        assertEquals("P12345", result.listANumber());
        assertEquals(LocalDate.of(2030, 12, 1), result.listAExpiry());
        assertEquals("I94-98765", result.i94Number());
        assertEquals(LocalDate.of(2027, 6, 30), result.i94AdmitUntil());
        assertEquals("P12345", result.passportNumber());
        assertEquals(LocalDate.of(2030, 12, 1), result.passportExpiry());
        assertTrue(result.populatedFields().contains("Document Title 1"));
    }

    @Test
    void extractsCitizenWithUsPassportOnly() throws Exception {
        byte[] pdf = buildI9Pdf(java.util.Map.of(
                "Last Name (Family Name)", "Smith",
                "First Name Given Name", "John",
                "FirstDayEmployed mmddyyyy", "03/01/2026",
                "Document Title 1", "U.S. Passport",
                "Document Number 0 (if any)", "US999",
                "Expiration Date if any", "03/01/2035"
        ), "CB_1");

        I9PdfExtraction result = I9PdfExtractor.extract(new ByteArrayInputStream(pdf));

        assertEquals(I9Rules.Attestation.CITIZEN.name(), result.attestation());
        assertEquals(I9Rules.A_US_PASSPORT, result.listACode());
        assertNull(result.i94Number(), "no 'i94' Document Title 2 was set, so no I-94 should be inferred");
    }

    @Test
    void noAcroFormYieldsEmptyExtractionNotGuessedData() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);

            I9PdfExtraction result = I9PdfExtractor.extract(new ByteArrayInputStream(out.toByteArray()));

            assertNull(result.validFrom());
            assertNull(result.docOption());
            assertEquals(List.of(), result.populatedFields());
        }
    }
}
